package me.topilov.springplayground.auth.passkey.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("app.passkeys")
data class PasskeyProperties(
    var rpId: String = "localhost",
    var rpName: String = "Spring Playground",
    var origins: List<String> = listOf("http://localhost:3000"),
    var ceremonyTtl: Duration = Duration.ofMinutes(5),
)
