package me.topilov.springplayground.auth

import me.topilov.springplayground.auth.service.StoredTwoFactorLoginChallenge
import me.topilov.springplayground.auth.service.TwoFactorLoginChallengeStore
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

@TestConfiguration
class TestTwoFactorConfiguration {
    @Bean
    fun inMemoryTwoFactorLoginChallengeStore(): InMemoryTwoFactorLoginChallengeStore = InMemoryTwoFactorLoginChallengeStore()

    @Bean
    @Primary
    fun twoFactorLoginChallengeStore(
        inMemoryTwoFactorLoginChallengeStore: InMemoryTwoFactorLoginChallengeStore,
    ): TwoFactorLoginChallengeStore = inMemoryTwoFactorLoginChallengeStore
}

class InMemoryTwoFactorLoginChallengeStore : TwoFactorLoginChallengeStore {
    private val challenges = ConcurrentHashMap<String, StoredTwoFactorLoginChallenge>()

    override fun createChallengeId(): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    override fun save(challenge: StoredTwoFactorLoginChallenge) {
        challenges[challenge.loginChallengeId] = challenge
    }

    override fun find(loginChallengeId: String): StoredTwoFactorLoginChallenge? = challenges[loginChallengeId]

    override fun consume(loginChallengeId: String): StoredTwoFactorLoginChallenge? = challenges.remove(loginChallengeId)

    fun clear() {
        challenges.clear()
    }

    companion object {
        private val secureRandom = SecureRandom()
    }
}
