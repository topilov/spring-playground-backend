package me.topilov.springplayground.auth

import me.topilov.springplayground.auth.reset.PasswordResetTokenStore
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

@TestConfiguration
class TestPasswordResetConfiguration {
    @Bean
    fun inMemoryPasswordResetTokenStore(): InMemoryPasswordResetTokenStore = InMemoryPasswordResetTokenStore()

    @Bean
    @Primary
    fun passwordResetTokenStore(
        inMemoryPasswordResetTokenStore: InMemoryPasswordResetTokenStore,
    ): PasswordResetTokenStore = inMemoryPasswordResetTokenStore
}

class InMemoryPasswordResetTokenStore : PasswordResetTokenStore {
    private val tokenToUserId = ConcurrentHashMap<String, Long>()
    private val userToTokens = ConcurrentHashMap<Long, MutableSet<String>>()

    fun clear() {
        tokenToUserId.clear()
        userToTokens.clear()
    }

    override fun createToken(): String = generateToken()

    override fun activateToken(userId: Long, rawToken: String) {
        val normalizedToken = rawToken.trim()
        tokenToUserId[normalizedToken] = userId
        userToTokens.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.add(normalizedToken)
    }

    override fun findUserId(rawToken: String): Long? = tokenToUserId[rawToken.trim()]

    override fun invalidateToken(rawToken: String) {
        val normalizedToken = rawToken.trim()
        val userId = tokenToUserId.remove(normalizedToken) ?: return
        userToTokens[userId]?.remove(normalizedToken)
        if (userToTokens[userId].isNullOrEmpty()) {
            userToTokens.remove(userId)
        }
    }

    override fun invalidateAllTokensForUser(userId: Long) {
        val tokens = userToTokens.remove(userId).orEmpty()
        tokens.forEach(tokenToUserId::remove)
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
