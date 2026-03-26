package me.topilov.springplayground.telegram.infrastructure.config

import me.topilov.springplayground.telegram.domain.TelegramFocusMode
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("app.telegram")
data class TelegramProperties(
    var enabled: Boolean = false,
    var sessionRoot: String = "telegram-sessions",
    var pendingAuthTtl: Duration = Duration.ofMinutes(10),
    var automationTokenBytes: Int = 32,
    var encryptionKeyBase64: String = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    var defaultFocusMappings: MutableMap<TelegramFocusMode, String> = mutableMapOf(),
    var apiId: Int = 0,
    var apiHash: String = "",
)
