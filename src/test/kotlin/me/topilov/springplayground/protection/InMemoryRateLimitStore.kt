package me.topilov.springplayground.protection

import me.topilov.springplayground.protection.infrastructure.store.CounterState
import me.topilov.springplayground.protection.infrastructure.store.RateLimitStore
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class InMemoryRateLimitStore : RateLimitStore {
    private val counters = ConcurrentHashMap<String, StoredCounter>()

    fun clear() {
        counters.clear()
    }

    override fun increment(key: String, ttl: Duration): CounterState {
        val now = Instant.now()
        counters.compute(key) { _, current ->
            when {
                current == null || current.expiresAt <= now -> StoredCounter(1, now.plus(ttl))
                else -> current.copy(count = current.count + 1)
            }
        }

        return requireNotNull(get(key))
    }

    override fun get(key: String): CounterState? {
        val current = counters[key] ?: return null
        val remaining = Duration.between(Instant.now(), current.expiresAt)
        if (!remaining.isPositive) {
            counters.remove(key)
            return null
        }

        return CounterState(current.count, remaining)
    }

    override fun reset(key: String) {
        counters.remove(key)
    }

    private data class StoredCounter(
        val count: Long,
        val expiresAt: Instant,
    )
}
