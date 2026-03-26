package me.topilov.springplayground.telegram.infrastructure.repository

import me.topilov.springplayground.telegram.domain.TelegramMode
import org.springframework.data.jpa.repository.JpaRepository

interface TelegramModeRepository : JpaRepository<TelegramMode, Long> {
    fun findAllByUserIdOrderByModeKeyAsc(userId: Long): List<TelegramMode>
    fun findByUserIdAndModeKey(userId: Long, modeKey: String): TelegramMode?
}
