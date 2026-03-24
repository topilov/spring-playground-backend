package me.topilov.springplayground.abuse

import me.topilov.springplayground.abuse.captcha.CaptchaVerificationResult
import me.topilov.springplayground.abuse.captcha.CaptchaVerificationService
import me.topilov.springplayground.abuse.store.CooldownStore
import me.topilov.springplayground.abuse.store.CounterState
import me.topilov.springplayground.abuse.store.RateLimitStore
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@TestConfiguration
class TestAbuseProtectionConfiguration {
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

class InMemoryRateLimitStore : RateLimitStore {
    private val counters = ConcurrentHashMap<String, StoredCounter>()

    fun clear() {
        counters.clear()
    }

    override fun increment(key: String, ttl: Duration): CounterState {
        val now = Instant.now()
        counters.compute(key) { _, current ->
            when {
                current == null || current.expiresAt <= now -> StoredCounter(1, now.plus(ttl))
                else -> current.copy(count = current.count + 1)
            }
        }

        return requireNotNull(get(key))
    }

    override fun get(key: String): CounterState? {
        val current = counters[key] ?: return null
        val remaining = Duration.between(Instant.now(), current.expiresAt)
        if (!remaining.isPositive) {
            counters.remove(key)
            return null
        }

        return CounterState(current.count, remaining)
    }

    override fun reset(key: String) {
        counters.remove(key)
    }

    private data class StoredCounter(
        val count: Long,
        val expiresAt: Instant,
    )
}

class InMemoryCooldownStore : CooldownStore {
    private val cooldowns = ConcurrentHashMap<String, Instant>()

    fun clear() {
        cooldowns.clear()
    }

    override fun activateIfAbsent(key: String, ttl: Duration): Duration? {
        val now = Instant.now()
        val existing = cooldowns[key]
        if (existing != null) {
            val remaining = Duration.between(now, existing)
            if (remaining.isPositive) {
                return remaining
            }
        }

        cooldowns[key] = now.plus(ttl)
        return null
    }
}

class FakeCaptchaVerificationService : CaptchaVerificationService {
    override fun verify(
        flow: AbuseProtectionFlow,
        token: String,
        remoteIp: String?,
    ): CaptchaVerificationResult = when (token.trim()) {
        "",
        "invalid-captcha-token",
        -> CaptchaVerificationResult(success = false, errorCodes = listOf("invalid-input-response"))

        "duplicate-captcha-token" -> CaptchaVerificationResult(
            success = false,
            errorCodes = listOf("timeout-or-duplicate"),
        )

        "internal-error-captcha-token" -> CaptchaVerificationResult(
            success = false,
            errorCodes = listOf("internal-error"),
        )

        else -> CaptchaVerificationResult(success = true)
    }
}
