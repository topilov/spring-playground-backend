package me.topilov.springplayground.profile

import me.topilov.springplayground.profile.emailchange.PendingEmailChange
import me.topilov.springplayground.profile.emailchange.PendingEmailChangeTokenStore
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

@TestConfiguration
class TestPendingEmailChangeConfiguration {
    @Bean
    fun inMemoryPendingEmailChangeTokenStore(): InMemoryPendingEmailChangeTokenStore = InMemoryPendingEmailChangeTokenStore()

    @Bean
    @Primary
    fun pendingEmailChangeTokenStore(
        inMemoryPendingEmailChangeTokenStore: InMemoryPendingEmailChangeTokenStore,
    ): PendingEmailChangeTokenStore = inMemoryPendingEmailChangeTokenStore
}

class InMemoryPendingEmailChangeTokenStore : PendingEmailChangeTokenStore {
    private val tokenToPendingChange = ConcurrentHashMap<String, PendingEmailChange>()
    private val userToTokens = ConcurrentHashMap<Long, MutableSet<String>>()

    fun clear() {
        tokenToPendingChange.clear()
        userToTokens.clear()
    }

    override fun createToken(): String = generateToken()

    override fun activateToken(userId: Long, newEmail: String, rawToken: String) {
        tokenToPendingChange[rawToken] = PendingEmailChange(userId = userId, newEmail = newEmail.trim().lowercase())
        userToTokens.computeIfAbsent(userId) { mutableSetOf() }.add(rawToken)
    }

    override fun findPendingChange(rawToken: String): PendingEmailChange? = tokenToPendingChange[rawToken.trim()]

    override fun invalidateToken(rawToken: String) {
        val normalizedToken = rawToken.trim()
        val pendingChange = tokenToPendingChange.remove(normalizedToken) ?: return
        userToTokens[pendingChange.userId]?.remove(normalizedToken)
        if (userToTokens[pendingChange.userId].isNullOrEmpty()) {
            userToTokens.remove(pendingChange.userId)
        }
    }

    override fun invalidateAllTokensForUser(userId: Long) {
        val tokens = userToTokens.remove(userId).orEmpty()
        tokens.forEach(tokenToPendingChange::remove)
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
