package me.topilov.springplayground.telegram.infrastructure.repository

import me.topilov.springplayground.telegram.domain.TelegramFocusMapping
import org.springframework.data.jpa.repository.JpaRepository

interface TelegramFocusMappingRepository : JpaRepository<TelegramFocusMapping, Long> {
    fun findAllByUserId(userId: Long): List<TelegramFocusMapping>
}
