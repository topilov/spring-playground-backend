package me.topilov.springplayground.abuse.store

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisRateLimitStore(
    private val redisTemplate: StringRedisTemplate,
) : RateLimitStore {
    override fun increment(key: String, ttl: Duration): CounterState {
        val count = redisTemplate.opsForValue().increment(key) ?: 0L
        if (count == 1L) {
            redisTemplate.expire(key, ttl)
        }

        return CounterState(
            count = count,
            expiresIn = remainingTtl(key, ttl),
        )
    }

    override fun get(key: String): CounterState? {
        val rawValue = redisTemplate.opsForValue().get(key)?.toLongOrNull() ?: return null
        return CounterState(
            count = rawValue,
            expiresIn = remainingTtl(key, Duration.ZERO),
        )
    }

    override fun reset(key: String) {
        redisTemplate.delete(key)
    }

    private fun remainingTtl(key: String, fallback: Duration): Duration {
        val ttlSeconds = redisTemplate.getExpire(key)
        return when {
            ttlSeconds > 0 -> Duration.ofSeconds(ttlSeconds)
            ttlSeconds == 0L -> Duration.ofSeconds(1)
            fallback.isPositive -> fallback
            else -> Duration.ofSeconds(1)
        }
    }
}
