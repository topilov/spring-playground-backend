package me.topilov.springplayground.mail

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("app.mail")
data class MailProperties(
    val apiKey: String = "",
    val from: String = "no-reply@example.com",
    val appName: String = "Spring Playground",
    val publicBaseUrl: String = "",
    val verifyEmailPath: String = "/verify-email",
    val emailVerificationTtl: Duration = Duration.ofHours(24),
    val resetPasswordPath: String = "/reset-password",
    val passwordResetTtl: Duration = Duration.ofMinutes(30),
)
