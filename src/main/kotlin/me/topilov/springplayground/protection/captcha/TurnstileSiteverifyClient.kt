package me.topilov.springplayground.protection.captcha

import com.fasterxml.jackson.databind.ObjectMapper
import me.topilov.springplayground.protection.infrastructure.config.TurnstileProperties
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.UUID

@Component
class TurnstileSiteverifyClient(
    private val objectMapper: ObjectMapper,
    private val turnstileProperties: TurnstileProperties,
) {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(turnstileProperties.timeout)
        .build()

    fun verify(token: String, remoteIp: String?): TurnstileSiteverifyExchange {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(turnstileProperties.siteverifyUrl))
            .timeout(turnstileProperties.timeout)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(token, remoteIp)))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return TurnstileSiteverifyExchange(
            statusCode = response.statusCode(),
            payload = objectMapper.readValue(response.body(), TurnstileSiteverifyResponse::class.java),
        )
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
}
