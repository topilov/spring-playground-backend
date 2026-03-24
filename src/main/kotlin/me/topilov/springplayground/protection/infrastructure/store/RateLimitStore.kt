package me.topilov.springplayground.protection.infrastructure.store

import java.time.Duration

interface RateLimitStore {
    fun increment(key: String, ttl: Duration): CounterState
    fun get(key: String): CounterState?
    fun reset(key: String)
}
