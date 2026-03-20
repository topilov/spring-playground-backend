package me.topilov.springplayground.mail

import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine

@Service
class ThymeleafEmailService(
    private val javaMailSender: JavaMailSender,
    private val templateEngine: SpringTemplateEngine,
    private val mailProperties: MailProperties,
) : EmailService {
    override fun sendWelcomeEmail(
        recipientEmail: String,
        username: String,
    ) = sendHtmlEmail(
        recipientEmail = recipientEmail,
        subject = "Welcome to ${mailProperties.appName}",
        templateName = "mail/welcome",
        variables = mapOf(
            "appName" to mailProperties.appName,
            "username" to username,
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
        val message = javaMailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, "UTF-8")
        helper.setFrom(mailProperties.from)
        helper.setTo(recipientEmail)
        helper.setSubject(subject)
        helper.setText(htmlBody, true)

        javaMailSender.send(message)
    }
}
