package me.topilov.springplayground.abuse

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import me.topilov.springplayground.abuse.captcha.TurnstileCaptchaVerificationService
import me.topilov.springplayground.abuse.config.TurnstileProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Duration

class TurnstileCaptchaVerificationServiceTest {
    private var server: HttpServer? = null

    @AfterEach
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun `blank token fails without remote validation`() {
        val service = TurnstileCaptchaVerificationService(
            TurnstileProperties(
                enabled = true,
                secretKey = "test-secret",
            ),
        )

        val result = service.verify(AbuseProtectionFlow.LOGIN, "", "127.0.0.1")

        assertThat(result.success).isFalse()
        assertThat(result.errorCodes).contains("invalid-input-response")
    }

    @Test
    fun `successful verification requires matching action and hostname when configured`() {
        server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/siteverify", JsonHandler("""{"success":true,"action":"login","hostname":"auth.example.com","error-codes":[]}"""))
            start()
        }

        val service = TurnstileCaptchaVerificationService(
            TurnstileProperties(
                enabled = true,
                secretKey = "test-secret",
                siteverifyUrl = "http://127.0.0.1:${server!!.address.port}/siteverify",
                expectedHostname = "auth.example.com",
                timeout = Duration.ofSeconds(1),
            ),
        )

        val result = service.verify(AbuseProtectionFlow.LOGIN, "valid-token", "127.0.0.1")

        assertThat(result.success).isTrue()
    }

    private class JsonHandler(private val body: String) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }
}
