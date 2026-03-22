package me.topilov.springplayground.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = emptyList(),
)
