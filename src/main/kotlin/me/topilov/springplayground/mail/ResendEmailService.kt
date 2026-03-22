package me.topilov.springplayground.mail

import com.resend.Resend
import com.resend.services.emails.model.CreateEmailOptions
import org.springframework.stereotype.Service
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine

@Service
class ResendEmailService(
    private val resend: Resend,
    private val templateEngine: SpringTemplateEngine,
    private val mailProperties: MailProperties,
) : EmailService {
    override fun sendVerificationEmail(
        recipientEmail: String,
        username: String,
        verificationUrl: String,
        expiresInMinutes: Long,
    ) = sendHtmlEmail(
        recipientEmail = recipientEmail,
        subject = "Verify your ${mailProperties.appName} email",
        templateName = "mail/verify-email",
        variables = mapOf(
            "appName" to mailProperties.appName,
            "username" to username,
            "verificationUrl" to verificationUrl,
            "expiresInMinutes" to expiresInMinutes,
        ),
    )

    override fun sendResetPasswordEmail(
        recipientEmail: String,
        username: String,
        resetUrl: String,
        expiresInMinutes: Long,
    ) = sendHtmlEmail(
        recipientEmail = recipientEmail,
        subject = "Reset your ${mailProperties.appName} password",
        templateName = "mail/reset-password",
        variables = mapOf(
            "appName" to mailProperties.appName,
            "username" to username,
            "resetUrl" to resetUrl,
            "expiresInMinutes" to expiresInMinutes,
        ),
    )

    private fun sendHtmlEmail(
        recipientEmail: String,
        subject: String,
        templateName: String,
        variables: Map<String, Any>,
    ) {
        val context = Context().apply {
            variables.forEach(::setVariable)
        }

        val htmlBody = templateEngine.process(templateName, context)

        val params = CreateEmailOptions.builder()
            .from(mailProperties.from)
            .to(recipientEmail)
            .subject(subject)
            .html(htmlBody)
            .build()

        resend.emails().send(params)
    }
}
