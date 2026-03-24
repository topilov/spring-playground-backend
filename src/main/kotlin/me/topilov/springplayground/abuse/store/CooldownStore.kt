package me.topilov.springplayground.abuse.store

import java.time.Duration

interface CooldownStore {
    fun activateIfAbsent(key: String, ttl: Duration): Duration?
}
