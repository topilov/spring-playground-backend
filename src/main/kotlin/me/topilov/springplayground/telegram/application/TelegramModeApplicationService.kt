package me.topilov.springplayground.telegram.application

import me.topilov.springplayground.telegram.domain.TelegramAccountConnection
import me.topilov.springplayground.telegram.domain.TelegramConnectionStatus
import me.topilov.springplayground.telegram.domain.TelegramMode
import me.topilov.springplayground.telegram.domain.exception.TelegramModeAlreadyExistsException
import me.topilov.springplayground.telegram.domain.exception.TelegramModeInvalidException
import me.topilov.springplayground.telegram.domain.exception.TelegramModeNotFoundException
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramAccountConnectionRepository
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramModeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TelegramModeApplicationService(
    private val modeRepository: TelegramModeRepository,
    private val accountConnectionRepository: TelegramAccountConnectionRepository,
) {
    @Transactional(readOnly = true)
    fun listModes(userId: Long): List<TelegramMode> =
        modeRepository.findAllByUserIdOrderByModeKeyAsc(userId)

    @Transactional
    fun createMode(userId: Long, mode: String, emojiStatusDocumentId: String): TelegramMode {
        val normalizedMode = normalizeModeKey(mode)
        val normalizedEmojiId = normalizeEmojiStatusDocumentId(emojiStatusDocumentId)
        if (modeRepository.findByUserIdAndModeKey(userId, normalizedMode) != null) {
            throw TelegramModeAlreadyExistsException(normalizedMode)
        }
        return modeRepository.save(
            TelegramMode(
                userId = userId,
                modeKey = normalizedMode,
                emojiStatusDocumentId = normalizedEmojiId,
            ),
        )
    }

    @Transactional
    fun updateMode(
        userId: Long,
        mode: String,
        newMode: String?,
        emojiStatusDocumentId: String?,
    ): TelegramMode {
        if (newMode == null && emojiStatusDocumentId == null) {
            throw TelegramModeInvalidException("Telegram mode update requires at least one field.")
        }

        val existing = modeRepository.findByUserIdAndModeKey(userId, normalizeModeKey(mode))
            ?: throw TelegramModeNotFoundException(mode)

        val normalizedNewMode = newMode?.let(::normalizeModeKey)
        if (normalizedNewMode != null && normalizedNewMode != existing.modeKey &&
            modeRepository.findByUserIdAndModeKey(userId, normalizedNewMode) != null
        ) {
            throw TelegramModeAlreadyExistsException(normalizedNewMode)
        }

        normalizedNewMode?.let { existing.modeKey = it }
        emojiStatusDocumentId?.let { existing.emojiStatusDocumentId = normalizeEmojiStatusDocumentId(it) }
        return modeRepository.save(existing)
    }

    @Transactional
    fun deleteMode(userId: Long, mode: String) {
        val existing = modeRepository.findByUserIdAndModeKey(userId, normalizeModeKey(mode))
            ?: throw TelegramModeNotFoundException(mode)
        val connection = accountConnectionRepository.findByUserId(userId)
            .orElseGet { TelegramAccountConnection(userId = userId, connectionStatus = TelegramConnectionStatus.DISCONNECTED) }
        if (connection.activeModeId == existing.id) {
            connection.activeModeId = null
            accountConnectionRepository.save(connection)
        }
        modeRepository.delete(existing)
    }

    private fun normalizeModeKey(value: String): String =
        value.trim()
            .takeIf { it.isNotBlank() && it.length <= 64 }
            ?: throw TelegramModeInvalidException("Telegram mode must be a non-blank string up to 64 characters.")

    private fun normalizeEmojiStatusDocumentId(value: String): String =
        value.trim()
            .takeIf { it.isNotBlank() }
            ?: throw TelegramModeInvalidException("Telegram emoji status document id must be non-blank.")
}
