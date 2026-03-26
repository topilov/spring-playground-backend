package me.topilov.springplayground.telegram.infrastructure.repository

import me.topilov.springplayground.telegram.domain.TelegramFocusMode
import me.topilov.springplayground.telegram.domain.TelegramFocusState
import org.springframework.data.jpa.repository.JpaRepository

interface TelegramFocusStateRepository : JpaRepository<TelegramFocusState, Long> {
    fun findAllByUserId(userId: Long): List<TelegramFocusState>
    fun findByUserIdAndFocusMode(userId: Long, focusMode: TelegramFocusMode): TelegramFocusState?
}
