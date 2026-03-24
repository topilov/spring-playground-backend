package me.topilov.springplayground.protection

import me.topilov.springplayground.protection.infrastructure.store.CooldownStore
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class InMemoryCooldownStore : CooldownStore {
    private val cooldowns = ConcurrentHashMap<String, Instant>()

    fun clear() {
        cooldowns.clear()
    }

    override fun activateIfAbsent(key: String, ttl: Duration): Duration? {
        val now = Instant.now()
        val existing = cooldowns[key]
        if (existing != null) {
            val remaining = Duration.between(now, existing)
            if (remaining.isPositive) {
                return remaining
            }
        }

        cooldowns[key] = now.plus(ttl)
        return null
    }
}
