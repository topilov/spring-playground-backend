package me.topilov.springplayground.mail

interface EmailService {
    fun sendWelcomeEmail(
        recipientEmail: String,
        username: String,
    )

    fun sendResetPasswordEmail(
        recipientEmail: String,
        username: String,
        resetUrl: String,
        expiresInMinutes: Long,
    )
}
