package me.topilov.springplayground.telegram.application

import me.topilov.springplayground.telegram.domain.TelegramAccountConnection
import me.topilov.springplayground.telegram.domain.TelegramConnectionStatus
import me.topilov.springplayground.telegram.domain.TelegramFocusMapping
import me.topilov.springplayground.telegram.domain.TelegramFocusMode
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramAccountConnectionRepository
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramFocusMappingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TelegramFocusSettingsService(
    private val accountConnectionRepository: TelegramAccountConnectionRepository,
    private val focusMappingRepository: TelegramFocusMappingRepository,
) {
    @Transactional
    fun updateSettings(
        userId: Long,
        defaultEmojiStatusDocumentId: String?,
        mappings: Map<TelegramFocusMode, String?>,
    ): TelegramAccountConnection {
        val connection = accountConnectionRepository.findByUserId(userId)
            .orElseGet { TelegramAccountConnection(userId = userId, connectionStatus = TelegramConnectionStatus.DISCONNECTED) }
        connection.defaultEmojiStatusDocumentId = defaultEmojiStatusDocumentId?.trim()?.takeIf { it.isNotBlank() }
        accountConnectionRepository.save(connection)

        val existingMappings = focusMappingRepository.findAllByUserId(userId).associateBy { it.focusMode }.toMutableMap()
        mappings.forEach { (mode, emojiId) ->
            val normalized = emojiId?.trim()?.takeIf { it.isNotBlank() }
            val existing = existingMappings[mode]
            when {
                normalized == null && existing != null -> focusMappingRepository.delete(existing)
                normalized != null && existing != null -> {
                    existing.emojiStatusDocumentId = normalized
                    focusMappingRepository.save(existing)
                }
                normalized != null -> focusMappingRepository.save(
                    TelegramFocusMapping(
                        userId = userId,
                        focusMode = mode,
                        emojiStatusDocumentId = normalized,
                    ),
                )
            }
        }

        return connection
    }
}
