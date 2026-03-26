package me.topilov.springplayground.telegram.infrastructure.repository

import me.topilov.springplayground.telegram.domain.TelegramAutomationToken
import org.springframework.data.jpa.repository.JpaRepository

interface TelegramAutomationTokenRepository : JpaRepository<TelegramAutomationToken, Long> {
    fun findByUserId(userId: Long): TelegramAutomationToken?
    fun findByUserIdAndRevokedAtIsNull(userId: Long): TelegramAutomationToken?
    fun findByTokenHashAndRevokedAtIsNull(tokenHash: String): TelegramAutomationToken?
}
