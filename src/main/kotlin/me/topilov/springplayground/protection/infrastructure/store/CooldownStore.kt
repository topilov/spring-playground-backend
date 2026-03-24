package me.topilov.springplayground.protection.infrastructure.store

import java.time.Duration

interface CooldownStore {
    fun activateIfAbsent(key: String, ttl: Duration): Duration?
}
