package me.topilov.springplayground.telegram.infrastructure.config

import me.topilov.springplayground.telegram.infrastructure.tdlight.TdlightTelegramClientGateway
import me.topilov.springplayground.telegram.infrastructure.tdlight.TelegramClientGateway
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
@EnableConfigurationProperties(TelegramProperties::class)
class TelegramConfiguration {
    @Bean
    fun telegramClock(): Clock = Clock.systemUTC()

    @Bean
    @ConditionalOnMissingBean(TelegramClientGateway::class)
    fun telegramClientGateway(properties: TelegramProperties): TelegramClientGateway =
        TdlightTelegramClientGateway(properties)
}
