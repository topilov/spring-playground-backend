package me.topilov.springplayground.telegram.infrastructure.repository

import me.topilov.springplayground.telegram.domain.TelegramAccountConnection
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface TelegramAccountConnectionRepository : JpaRepository<TelegramAccountConnection, Long> {
    fun findByUserId(userId: Long): Optional<TelegramAccountConnection>
}
