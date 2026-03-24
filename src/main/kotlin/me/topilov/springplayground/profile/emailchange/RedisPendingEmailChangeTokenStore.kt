package me.topilov.springplayground.profile.emailchange

import me.topilov.springplayground.mail.MailProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

@Component
class RedisPendingEmailChangeTokenStore(
    private val redisTemplate: StringRedisTemplate,
    mailProperties: MailProperties,
) : PendingEmailChangeTokenStore {
    private val ttl: Duration = mailProperties.emailVerificationTtl

    override fun createToken(): String = generateToken()

    override fun activateToken(userId: Long, newEmail: String, rawToken: String) {
        val tokenHash = hashToken(rawToken)
        val normalizedEmail = newEmail.trim().lowercase()
        redisTemplate.opsForValue().set(tokenKey(tokenHash), "$userId|$normalizedEmail", ttl)
        redisTemplate.opsForSet().add(userTokensKey(userId), tokenHash)
        redisTemplate.expire(userTokensKey(userId), ttl)
    }

    override fun findPendingChange(rawToken: String): PendingEmailChange? {
        val value = redisTemplate.opsForValue().get(tokenKey(hashToken(rawToken.trim()))) ?: return null
        val separatorIndex = value.indexOf('|')
        if (separatorIndex <= 0 || separatorIndex == value.lastIndex) {
            return null
        }

        val userId = value.substring(0, separatorIndex).toLongOrNull() ?: return null
        val newEmail = value.substring(separatorIndex + 1)
        if (newEmail.isBlank()) {
            return null
        }

        return PendingEmailChange(
            userId = userId,
            newEmail = newEmail,
        )
    }

    override fun invalidateToken(rawToken: String) {
        val tokenHash = hashToken(rawToken.trim())
        val pendingChange = findPendingChange(rawToken)
        redisTemplate.delete(tokenKey(tokenHash))
        if (pendingChange != null) {
            redisTemplate.opsForSet().remove(userTokensKey(pendingChange.userId), tokenHash)
        }
    }

    override fun invalidateAllTokensForUser(userId: Long) {
        val tokenHashes = redisTemplate.opsForSet().members(userTokensKey(userId)).orEmpty()
        if (tokenHashes.isEmpty()) {
            redisTemplate.delete(userTokensKey(userId))
            return
        }

        redisTemplate.delete(tokenHashes.map(::tokenKey))
        redisTemplate.delete(userTokensKey(userId))
    }

    private fun tokenKey(tokenHash: String): String = "profile:email-change:token:$tokenHash"

    private fun userTokensKey(userId: Long): String = "profile:email-change:user:$userId"

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashToken(rawToken: String): String = MessageDigest.getInstance("SHA-256")
        .digest(rawToken.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    companion object {
        private val secureRandom = SecureRandom()
    }
}
