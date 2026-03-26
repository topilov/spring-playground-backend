package me.topilov.springplayground.telegram

import me.topilov.springplayground.telegram.application.TelegramPendingAuthStore
import me.topilov.springplayground.telegram.infrastructure.tdlight.TelegramClientGateway
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class TestTelegramConfiguration {
    @Bean
    fun fakeTelegramClientGateway(): FakeTelegramClientGateway = FakeTelegramClientGateway()

    @Bean
    @Primary
    fun telegramClientGateway(fakeTelegramClientGateway: FakeTelegramClientGateway): TelegramClientGateway =
        fakeTelegramClientGateway

    @Bean
    fun inMemoryTelegramPendingAuthStore(): InMemoryTelegramPendingAuthStore = InMemoryTelegramPendingAuthStore()

    @Bean
    @Primary
    fun telegramPendingAuthStore(
        inMemoryTelegramPendingAuthStore: InMemoryTelegramPendingAuthStore,
    ): TelegramPendingAuthStore = inMemoryTelegramPendingAuthStore
}
