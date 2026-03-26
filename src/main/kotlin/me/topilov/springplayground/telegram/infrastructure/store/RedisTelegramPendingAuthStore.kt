package me.topilov.springplayground.telegram.infrastructure.store

import com.fasterxml.jackson.databind.ObjectMapper
import me.topilov.springplayground.telegram.application.TelegramPendingAuth
import me.topilov.springplayground.telegram.application.TelegramPendingAuthStore
import me.topilov.springplayground.telegram.infrastructure.config.TelegramProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisTelegramPendingAuthStore(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val telegramProperties: TelegramProperties,
) : TelegramPendingAuthStore {
    override fun save(pendingAuth: TelegramPendingAuth) {
        redisTemplate.opsForValue().set(
            key(pendingAuth.pendingAuthId),
            objectMapper.writeValueAsString(pendingAuth),
            telegramProperties.pendingAuthTtl,
        )
    }

    override fun findById(pendingAuthId: String): TelegramPendingAuth? =
        redisTemplate.opsForValue().get(key(pendingAuthId))
            ?.let { objectMapper.readValue(it, TelegramPendingAuth::class.java) }

    override fun delete(pendingAuthId: String) {
        redisTemplate.delete(key(pendingAuthId))
    }

    private fun key(pendingAuthId: String): String = "telegram:pending-auth:$pendingAuthId"
}
