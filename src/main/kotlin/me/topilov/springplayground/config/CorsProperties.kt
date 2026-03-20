package me.topilov.springplayground.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = listOf(
        "http://localhost:3000",
        "http://localhost:4173",
        "http://localhost:5173",
    ),
)
