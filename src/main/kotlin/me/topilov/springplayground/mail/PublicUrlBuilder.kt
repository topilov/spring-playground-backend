package me.topilov.springplayground.mail

import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Component
class PublicUrlBuilder(
    private val mailProperties: MailProperties,
) {
    fun build(pathValue: String, rawToken: String): String {
        val baseUrl = mailProperties.publicBaseUrl.trimEnd('/')
        val path = if (pathValue.startsWith("/")) {
            pathValue
        } else {
            "/$pathValue"
        }

        return "$baseUrl$path?token=${URLEncoder.encode(rawToken, StandardCharsets.UTF_8)}"
    }
}
