package me.topilov.springplayground.auth.verification

import me.topilov.springplayground.mail.MailProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

@Component
class RedisEmailVerificationTokenStore(
    private val redisTemplate: StringRedisTemplate,
    mailProperties: MailProperties,
) : EmailVerificationTokenStore {
    private val ttl: Duration = mailProperties.emailVerificationTtl

    override fun createToken(): String = generateToken()

    override fun activateToken(userId: Long, rawToken: String) {
        val tokenHash = hashToken(rawToken)
        redisTemplate.opsForValue().set(tokenKey(tokenHash), userId.toString(), ttl)
    }

    override fun findUserId(rawToken: String): Long? {
        val tokenHash = hashToken(rawToken.trim())
        val userIdValue = redisTemplate.opsForValue().get(tokenKey(tokenHash)) ?: return null
        return userIdValue.toLongOrNull()
    }

    override fun invalidateToken(rawToken: String) {
        redisTemplate.delete(tokenKey(hashToken(rawToken.trim())))
    }

    private fun tokenKey(tokenHash: String): String = "auth:email-verification:token:$tokenHash"

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
