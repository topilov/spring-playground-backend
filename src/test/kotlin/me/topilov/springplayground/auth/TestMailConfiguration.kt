package me.topilov.springplayground.auth

import me.topilov.springplayground.mail.EmailService
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class TestMailConfiguration {
    @Bean
    fun recordingJavaMailSender(): RecordingEmailService = RecordingEmailService()

    @Bean
    @Primary
    fun javaMailSender(recordingJavaMailSender: RecordingEmailService): EmailService = recordingJavaMailSender
}

class RecordingEmailService : EmailService {

    data class SentEmail(
        val kind: Kind,
        val recipientEmail: String,
        val username: String,
        val url: String,
        val expiresInMinutes: Long,
    ) {
        enum class Kind {
            VERIFICATION,
            RESET_PASSWORD,
        }
    }

    private val sentEmails = mutableListOf<SentEmail>()

    fun clear() {
        sentEmails.clear()
    }

    fun sentEmails(): List<SentEmail> = sentEmails.toList()

    override fun sendVerificationEmail(
        recipientEmail: String,
        username: String,
        verificationUrl: String,
        expiresInMinutes: Long,
    ) {
        sentEmails += SentEmail(
            kind = SentEmail.Kind.VERIFICATION,
            recipientEmail = recipientEmail,
            username = username,
            url = verificationUrl,
            expiresInMinutes = expiresInMinutes,
        )
    }

    override fun sendResetPasswordEmail(
        recipientEmail: String,
        username: String,
        resetUrl: String,
        expiresInMinutes: Long,
    ) {
        sentEmails += SentEmail(
            kind = SentEmail.Kind.RESET_PASSWORD,
            recipientEmail = recipientEmail,
            username = username,
            url = resetUrl,
            expiresInMinutes = expiresInMinutes,
        )
    }
}
