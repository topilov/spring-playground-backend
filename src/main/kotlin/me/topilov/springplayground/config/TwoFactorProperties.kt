package me.topilov.springplayground.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("app.two-factor")
data class TwoFactorProperties(
    var issuer: String = "Spring Playground",
    var loginChallengeTtl: Duration = Duration.ofMinutes(5),
    var encryptionKeyBase64: String = "",
    var backupCodeCount: Int = 10,
)
