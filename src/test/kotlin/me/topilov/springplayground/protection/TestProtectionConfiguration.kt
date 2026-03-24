package me.topilov.springplayground.protection

import me.topilov.springplayground.protection.captcha.CaptchaVerificationService
import me.topilov.springplayground.protection.infrastructure.store.CooldownStore
import me.topilov.springplayground.protection.infrastructure.store.RateLimitStore
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class TestProtectionConfiguration {
    @Bean
    fun inMemoryRateLimitStore(): InMemoryRateLimitStore = InMemoryRateLimitStore()

    @Bean
    @Primary
    fun rateLimitStore(inMemoryRateLimitStore: InMemoryRateLimitStore): RateLimitStore = inMemoryRateLimitStore

    @Bean
    fun inMemoryCooldownStore(): InMemoryCooldownStore = InMemoryCooldownStore()

    @Bean
    @Primary
    fun cooldownStore(inMemoryCooldownStore: InMemoryCooldownStore): CooldownStore = inMemoryCooldownStore

    @Bean
    fun fakeCaptchaVerificationService(): FakeCaptchaVerificationService = FakeCaptchaVerificationService()

    @Bean
    @Primary
    fun captchaVerificationService(
        fakeCaptchaVerificationService: FakeCaptchaVerificationService,
    ): CaptchaVerificationService = fakeCaptchaVerificationService
}
