package me.topilov.springplayground.auth.passkey.ceremony

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import me.topilov.springplayground.auth.passkey.config.PasskeyProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

@Component
class RedisPasskeyCeremonyStore(
    private val redisTemplate: StringRedisTemplate,
    passkeyProperties: PasskeyProperties,
) : PasskeyCeremonyStore {
    private val ttl: Duration = passkeyProperties.ceremonyTtl
    private val objectMapper = jacksonObjectMapper()

    override fun createCeremonyId(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    override fun saveRegistration(ceremony: RegistrationPasskeyCeremony) {
        redisTemplate.opsForValue().set(
            registrationKey(ceremony.ceremonyId),
            objectMapper.writeValueAsString(ceremony),
            ttl,
        )
    }

    override fun findRegistration(ceremonyId: String): RegistrationPasskeyCeremony? =
        redisTemplate.opsForValue().get(registrationKey(ceremonyId.trim()))
            ?.let { objectMapper.readValue(it, RegistrationPasskeyCeremony::class.java) }

    override fun invalidateRegistration(ceremonyId: String) {
        redisTemplate.delete(registrationKey(ceremonyId.trim()))
    }

    override fun saveAuthentication(ceremony: AuthenticationPasskeyCeremony) {
        redisTemplate.opsForValue().set(
            authenticationKey(ceremony.ceremonyId),
            objectMapper.writeValueAsString(ceremony),
            ttl,
        )
    }

    override fun findAuthentication(ceremonyId: String): AuthenticationPasskeyCeremony? =
        redisTemplate.opsForValue().get(authenticationKey(ceremonyId.trim()))
            ?.let { objectMapper.readValue(it, AuthenticationPasskeyCeremony::class.java) }

    override fun invalidateAuthentication(ceremonyId: String) {
        redisTemplate.delete(authenticationKey(ceremonyId.trim()))
    }

    private fun registrationKey(ceremonyId: String): String = "auth:passkey:registration:$ceremonyId"

    private fun authenticationKey(ceremonyId: String): String = "auth:passkey:authentication:$ceremonyId"

    companion object {
        private val secureRandom = SecureRandom()
    }
}
