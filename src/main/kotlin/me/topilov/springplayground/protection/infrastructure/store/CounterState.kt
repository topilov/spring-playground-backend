package me.topilov.springplayground.protection.infrastructure.store

import java.time.Duration

data class CounterState(
    val count: Long,
    val expiresIn: Duration,
)
