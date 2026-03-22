package me.topilov.springplayground.mail

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(
    name = ["app.mail.enabled"],
    havingValue = "false",
    matchIfMissing = true
)
class NoopEmailService : EmailService {

    override fun sendVerificationEmail(
        recipientEmail: String,
        username: String,
        verificationUrl: String,
        expiresInMinutes: Long,
    ) = Unit

    override fun sendResetPasswordEmail(
        recipientEmail: String,
        username: String,
        resetUrl: String,
        expiresInMinutes: Long,
    ) = Unit
}