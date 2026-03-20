package me.topilov.springplayground.auth

import me.topilov.springplayground.auth.verification.EmailVerificationTokenStore
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

@TestConfiguration
class TestEmailVerificationConfiguration {
    @Bean
    fun inMemoryEmailVerificationTokenStore(): InMemoryEmailVerificationTokenStore = InMemoryEmailVerificationTokenStore()

    @Bean
    @Primary
    fun emailVerificationTokenStore(
        inMemoryEmailVerificationTokenStore: InMemoryEmailVerificationTokenStore,
    ): EmailVerificationTokenStore = inMemoryEmailVerificationTokenStore
}

class InMemoryEmailVerificationTokenStore : EmailVerificationTokenStore {
    private val tokenToUserId = ConcurrentHashMap<String, Long>()

    fun clear() {
        tokenToUserId.clear()
    }

    override fun createToken(): String = generateToken()

    override fun activateToken(userId: Long, rawToken: String) {
        tokenToUserId[rawToken] = userId
    }

    override fun findUserId(rawToken: String): Long? {
        val normalizedToken = rawToken.trim()
        return tokenToUserId[normalizedToken]
    }

    override fun invalidateToken(rawToken: String) {
        tokenToUserId.remove(rawToken.trim())
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        private val secureRandom = SecureRandom()
    }
}
