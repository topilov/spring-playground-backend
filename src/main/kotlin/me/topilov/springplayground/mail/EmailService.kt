package me.topilov.springplayground.mail

interface EmailService {
    fun sendRegistrationVerificationEmail(
        recipientEmail: String,
        username: String,
        verificationUrl: String,
        expiresInMinutes: Long,
    )

    fun sendEmailChangeVerificationEmail(
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
