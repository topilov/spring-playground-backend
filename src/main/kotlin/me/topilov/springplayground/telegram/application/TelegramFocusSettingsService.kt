package me.topilov.springplayground.telegram.application

import me.topilov.springplayground.telegram.domain.TelegramAccountConnection
import me.topilov.springplayground.telegram.domain.TelegramConnectionStatus
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramAccountConnectionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TelegramFocusSettingsService(
    private val accountConnectionRepository: TelegramAccountConnectionRepository,
) {
    @Transactional
    fun updateSettings(
        userId: Long,
        defaultEmojiStatusDocumentId: String?,
    ): TelegramAccountConnection {
        val connection = accountConnectionRepository.findByUserId(userId)
            .orElseGet { TelegramAccountConnection(userId = userId, connectionStatus = TelegramConnectionStatus.DISCONNECTED) }
        connection.defaultEmojiStatusDocumentId = defaultEmojiStatusDocumentId?.trim()?.takeIf { it.isNotBlank() }
        return accountConnectionRepository.save(connection)
    }
}
