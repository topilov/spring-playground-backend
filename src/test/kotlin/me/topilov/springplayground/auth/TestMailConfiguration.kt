package me.topilov.springplayground.auth

import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import java.util.Properties

@TestConfiguration
class TestMailConfiguration {
    @Bean
    fun recordingJavaMailSender(): RecordingJavaMailSender = RecordingJavaMailSender()

    @Bean
    @Primary
    fun javaMailSender(recordingJavaMailSender: RecordingJavaMailSender): JavaMailSender = recordingJavaMailSender
}

class RecordingJavaMailSender : JavaMailSenderImpl() {
    private val session = Session.getInstance(Properties())
    private val sentMessages = mutableListOf<MimeMessage>()

    fun clear() {
        sentMessages.clear()
    }

    fun sentMessages(): List<MimeMessage> = sentMessages.toList()

    override fun createMimeMessage(): MimeMessage = MimeMessage(session)

    override fun send(mimeMessage: MimeMessage) {
        sentMessages += MimeMessage(mimeMessage)
    }

    override fun send(vararg mimeMessages: MimeMessage) {
        mimeMessages.forEach(::send)
    }

    override fun send(simpleMessage: SimpleMailMessage) {
        throw UnsupportedOperationException("SimpleMailMessage is not used in these tests")
    }

    override fun send(vararg simpleMessages: SimpleMailMessage) {
        throw UnsupportedOperationException("SimpleMailMessage is not used in these tests")
    }
}
