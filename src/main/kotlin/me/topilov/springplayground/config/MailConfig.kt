package me.topilov.springplayground.config

import com.resend.Resend
import me.topilov.springplayground.mail.MailProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("!test")
class MailConfig {

    @Bean
    fun resend(mailProperties: MailProperties): Resend {
        return Resend(mailProperties.apiKey)
    }
}