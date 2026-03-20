package me.topilov.springplayground.mail

interface EmailService {
    fun sendVerificationEmail(
        recipientEmail: String,
        username: String,
        verificationUrl: String,
        expiresInMinutes: Long,
    )

    fun sendResetPasswordEmail(
        recipientEmail: String,
        username: String,
        resetUrl: String,
        expiresInMinutes: Long,
    )
}
