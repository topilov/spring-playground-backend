package me.topilov.springplayground.protection.infrastructure.store

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisCooldownStore(
    private val redisTemplate: StringRedisTemplate,
) : CooldownStore {
    override fun activateIfAbsent(key: String, ttl: Duration): Duration? {
        val activated = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl)
        if (activated == true) {
            return null
        }

        val ttlSeconds = redisTemplate.getExpire(key)
        return when {
            ttlSeconds > 0 -> Duration.ofSeconds(ttlSeconds)
            else -> Duration.ofSeconds(1)
        }
    }
}
