package me.topilov.springplayground.abuse

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import me.topilov.springplayground.abuse.captcha.TurnstileCaptchaVerificationService
import me.topilov.springplayground.abuse.config.TurnstileProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.time.Duration

@ExtendWith(OutputCaptureExtension::class)
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

    @Test
    fun `successful verification ignores unknown turnstile response fields`() {
        server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext(
                "/siteverify",
                JsonHandler("""{"success":true,"action":"login","hostname":"auth.example.com","cdata":"opaque","error-codes":[]}"""),
            )
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

    @Test
    fun `successful verification tolerates blank action in turnstile response`() {
        server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext(
                "/siteverify",
                JsonHandler("""{"success":true,"action":"","hostname":"auth.example.com","error-codes":[]}"""),
            )
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

    @Test
    fun `network failures log root cause details and return internal error`(output: CapturedOutput) {
        val unusedPort = ServerSocket(0).use { it.localPort }
        val service = TurnstileCaptchaVerificationService(
            TurnstileProperties(
                enabled = true,
                secretKey = "test-secret",
                siteverifyUrl = "http://127.0.0.1:$unusedPort/siteverify",
                timeout = Duration.ofMillis(200),
            ),
        )

        val result = service.verify(AbuseProtectionFlow.LOGIN, "valid-token", "203.0.113.10")

        assertThat(result.success).isFalse()
        assertThat(result.errorCodes).containsExactly("internal-error")
        assertThat(output.out).contains("Turnstile verification request failed")
        assertThat(output.out).contains("flow=LOGIN")
        assertThat(output.out).contains("remoteIp=203.0.113.10")
        assertThat(output.out).contains("siteverifyUrl=http://127.0.0.1:$unusedPort/siteverify")
        assertThat(output.out).contains("exceptionClass=java.net.ConnectException")
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
