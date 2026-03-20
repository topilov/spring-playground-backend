package me.topilov.springplayground.auth.reset

import me.topilov.springplayground.mail.MailProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

@Component
class RedisPasswordResetTokenStore(
    private val redisTemplate: StringRedisTemplate,
    mailProperties: MailProperties,
) : PasswordResetTokenStore {
    private val ttl: Duration = mailProperties.passwordResetTtl

    override fun createToken(): String = generateToken()

    override fun activateToken(userId: Long, rawToken: String) {
        val tokenHash = hashToken(rawToken)
        redisTemplate.opsForValue().set(tokenKey(tokenHash), userId.toString(), ttl)
        redisTemplate.opsForSet().add(userTokensKey(userId), tokenHash)
        redisTemplate.expire(userTokensKey(userId), ttl)
    }

    override fun findUserId(rawToken: String): Long? {
        val userIdValue = redisTemplate.opsForValue().get(tokenKey(hashToken(rawToken.trim()))) ?: return null
        return userIdValue.toLongOrNull()
    }

    override fun invalidateToken(rawToken: String) {
        val tokenHash = hashToken(rawToken.trim())
        val userId = redisTemplate.opsForValue().get(tokenKey(tokenHash))?.toLongOrNull()
        redisTemplate.delete(tokenKey(tokenHash))
        if (userId != null) {
            redisTemplate.opsForSet().remove(userTokensKey(userId), tokenHash)
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

    private fun tokenKey(tokenHash: String): String = "auth:password-reset:token:$tokenHash"

    private fun userTokensKey(userId: Long): String = "auth:password-reset:user:$userId"

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
