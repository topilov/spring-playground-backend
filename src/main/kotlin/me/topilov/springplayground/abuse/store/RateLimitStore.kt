package me.topilov.springplayground.abuse.store

import java.time.Duration

interface RateLimitStore {
    fun increment(key: String, ttl: Duration): CounterState
    fun get(key: String): CounterState?
    fun reset(key: String)
}

data class CounterState(
    val count: Long,
    val expiresIn: Duration,
)
