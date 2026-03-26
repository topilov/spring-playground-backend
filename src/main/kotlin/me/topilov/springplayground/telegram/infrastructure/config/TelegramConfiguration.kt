package me.topilov.springplayground.telegram.infrastructure.config

import me.topilov.springplayground.telegram.domain.TelegramEmojiMappingResolver
import me.topilov.springplayground.telegram.infrastructure.tdlight.TdlightTelegramClientGateway
import me.topilov.springplayground.telegram.infrastructure.tdlight.TelegramClientGateway
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
    fun telegramEmojiMappingResolver(properties: TelegramProperties): TelegramEmojiMappingResolver =
        TelegramEmojiMappingResolver(properties.defaultFocusMappings.toMap())

    @Bean
    fun telegramClientGateway(properties: TelegramProperties): TelegramClientGateway =
        TdlightTelegramClientGateway(properties)
}
