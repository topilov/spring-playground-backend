package me.topilov.springplayground.abuse.captcha

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import me.topilov.springplayground.abuse.AbuseProtectionFlow
import me.topilov.springplayground.abuse.config.TurnstileProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.UUID

@Service
class TurnstileCaptchaVerificationService(
    private val turnstileProperties: TurnstileProperties,
) : CaptchaVerificationService {
    private val objectMapper = jacksonObjectMapper()
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(turnstileProperties.timeout)
        .build()

    override fun verify(flow: AbuseProtectionFlow, token: String, remoteIp: String?): CaptchaVerificationResult {
        if (!turnstileProperties.enabled) {
            return CaptchaVerificationResult(success = true)
        }

        val normalizedToken = token.trim()
        if (normalizedToken.isBlank() || normalizedToken.length > 2048) {
            return CaptchaVerificationResult(success = false, errorCodes = listOf("invalid-input-response"))
        }

        return try {
            val body = buildRequestBody(normalizedToken, remoteIp)
            val response = httpClient.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(turnstileProperties.siteverifyUrl))
                    .timeout(turnstileProperties.timeout)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            val payload = objectMapper.readValue(response.body(), TurnstileSiteverifyResponse::class.java)
            val expectedAction = turnstileProperties.actionFor(flow)
            val expectedHostname = turnstileProperties.expectedHostname?.takeIf { it.isNotBlank() }

            when {
                !payload.success -> {
                    log.warn(
                        "Turnstile rejected captcha flow={} remoteIp={} siteverifyUrl={} statusCode={} errorCodes={} action={} hostname={}",
                        flow,
                        remoteIp ?: "unknown",
                        turnstileProperties.siteverifyUrl,
                        response.statusCode(),
                        payload.errorCodes,
                        payload.action ?: "none",
                        payload.hostname ?: "none",
                    )
                    CaptchaVerificationResult(false, payload.errorCodes)
                }
                expectedAction != null && payload.action != null && payload.action != expectedAction ->
                    CaptchaVerificationResult(false, listOf("invalid-action")).also {
                        log.warn(
                            "Turnstile returned unexpected action flow={} remoteIp={} siteverifyUrl={} expectedAction={} actualAction={} hostname={}",
                            flow,
                            remoteIp ?: "unknown",
                            turnstileProperties.siteverifyUrl,
                            expectedAction,
                            payload.action,
                            payload.hostname ?: "none",
                        )
                    }
                expectedHostname != null && payload.hostname != null && payload.hostname != expectedHostname ->
                    CaptchaVerificationResult(false, listOf("invalid-hostname")).also {
                        log.warn(
                            "Turnstile returned unexpected hostname flow={} remoteIp={} siteverifyUrl={} expectedHostname={} actualHostname={} action={}",
                            flow,
                            remoteIp ?: "unknown",
                            turnstileProperties.siteverifyUrl,
                            expectedHostname,
                            payload.hostname,
                            payload.action ?: "none",
                        )
                    }
                else -> CaptchaVerificationResult(true)
            }
        } catch (exception: Exception) {
            log.warn(
                "Turnstile verification request failed flow={} remoteIp={} siteverifyUrl={} exceptionClass={} exceptionMessage={}",
                flow,
                remoteIp ?: "unknown",
                turnstileProperties.siteverifyUrl,
                exception.javaClass.name,
                exception.message ?: "none",
                exception,
            )
            CaptchaVerificationResult(success = false, errorCodes = listOf("internal-error"))
        }
    }

    private fun buildRequestBody(token: String, remoteIp: String?): String {
        val values = linkedMapOf(
            "secret" to turnstileProperties.secretKey,
            "response" to token,
            "idempotency_key" to UUID.randomUUID().toString(),
        )
        if (!remoteIp.isNullOrBlank()) {
            values["remoteip"] = remoteIp
        }

        return values.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    companion object {
        private val log = LoggerFactory.getLogger(TurnstileCaptchaVerificationService::class.java)
    }
}
