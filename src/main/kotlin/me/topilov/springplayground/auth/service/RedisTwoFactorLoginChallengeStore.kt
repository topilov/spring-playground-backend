package me.topilov.springplayground.auth.service

import com.fasterxml.jackson.databind.ObjectMapper
import me.topilov.springplayground.config.TwoFactorProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64

@Component
class RedisTwoFactorLoginChallengeStore(
    private val redisTemplate: StringRedisTemplate,
    private val twoFactorProperties: TwoFactorProperties,
    private val objectMapper: ObjectMapper,
) : TwoFactorLoginChallengeStore {
    override fun createChallengeId(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    override fun save(challenge: StoredTwoFactorLoginChallenge) {
        redisTemplate.opsForValue().set(
            key(challenge.loginChallengeId),
            objectMapper.writeValueAsString(challenge),
            twoFactorProperties.loginChallengeTtl,
        )
    }

    override fun find(loginChallengeId: String): StoredTwoFactorLoginChallenge? =
        redisTemplate.opsForValue().get(key(loginChallengeId.trim()))
            ?.let { objectMapper.readValue(it, StoredTwoFactorLoginChallenge::class.java) }

    override fun consume(loginChallengeId: String): StoredTwoFactorLoginChallenge? =
        redisTemplate.opsForValue().getAndDelete(key(loginChallengeId.trim()))
            ?.let { objectMapper.readValue(it, StoredTwoFactorLoginChallenge::class.java) }

    private fun key(loginChallengeId: String): String = "auth:2fa:login:${loginChallengeId.trim()}"

    companion object {
        private val secureRandom = SecureRandom()
    }
}
