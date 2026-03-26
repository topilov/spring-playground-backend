package me.topilov.springplayground.telegram.application

import jakarta.servlet.http.HttpServletRequest
import me.topilov.springplayground.protection.application.ProtectionService
import me.topilov.springplayground.protection.domain.ProtectionFlow
import me.topilov.springplayground.telegram.domain.TelegramConnectionStatus
import me.topilov.springplayground.telegram.domain.exception.TelegramModeInvalidException
import me.topilov.springplayground.telegram.domain.exception.TelegramModeNotFoundException
import me.topilov.springplayground.telegram.domain.exception.TelegramNotConnectedException
import me.topilov.springplayground.telegram.domain.exception.TelegramPremiumRequiredException
import me.topilov.springplayground.telegram.domain.exception.TelegramSyncFailedException
import me.topilov.springplayground.telegram.infrastructure.crypto.TelegramSessionSecretCrypto
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramAccountConnectionRepository
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramModeRepository
import me.topilov.springplayground.telegram.infrastructure.tdlight.TelegramClientGateway
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

data class TelegramFocusAutomationResult(
    val activeFocusMode: String?,
    val appliedEmojiStatusDocumentId: String?,
)

@Service
class TelegramFocusAutomationApplicationService(
    private val automationTokenService: TelegramAutomationTokenApplicationService,
    private val accountConnectionRepository: TelegramAccountConnectionRepository,
    private val modeRepository: TelegramModeRepository,
    private val telegramClientGateway: TelegramClientGateway,
    private val sessionSecretCrypto: TelegramSessionSecretCrypto,
    private val protectionService: ProtectionService,
    private val clock: Clock,
) {
    @Transactional
    fun applyFocusUpdate(
        rawToken: String,
        mode: String,
        active: Boolean,
        request: HttpServletRequest,
    ): TelegramFocusAutomationResult {
        val token = automationTokenService.findActiveToken(rawToken)
        val userId = token.userId
        protectionService.protect(
            ProtectionFlow.TELEGRAM_FOCUS_UPDATE,
            protectionService.buildContext("", request, userId.toString()),
        )

        val connection = accountConnectionRepository.findByUserId(userId).orElseThrow(::TelegramNotConnectedException)
        if (connection.connectionStatus != TelegramConnectionStatus.CONNECTED ||
            connection.telegramUserId == null ||
            connection.telegramPhoneNumber.isNullOrBlank() ||
            connection.sessionDirectoryKey.isNullOrBlank() ||
            connection.sessionDatabaseKeyCiphertext.isNullOrBlank()
        ) {
            throw TelegramNotConnectedException()
        }
        if (!connection.premium) {
            throw TelegramPremiumRequiredException()
        }

        val now = Instant.now(clock)
        val normalizedMode = normalizeModeKey(mode)
        val requestedMode = modeRepository.findByUserIdAndModeKey(userId, normalizedMode)
        val currentActiveMode = connection.activeModeId?.let(modeRepository::findById)?.orElse(null)

        val nextActiveMode = when {
            active -> requestedMode ?: throw TelegramModeNotFoundException(normalizedMode)
            currentActiveMode?.modeKey == normalizedMode -> null
            else -> currentActiveMode
        }
        val emojiStatusDocumentId = nextActiveMode?.emojiStatusDocumentId ?: connection.defaultEmojiStatusDocumentId

        try {
            telegramClientGateway.updateEmojiStatus(
                userId = userId,
                phoneNumber = requireNotNull(connection.telegramPhoneNumber),
                telegramUserId = requireNotNull(connection.telegramUserId),
                sessionDirectoryKey = requireNotNull(connection.sessionDirectoryKey),
                sessionDatabaseKey = sessionSecretCrypto.decrypt(requireNotNull(connection.sessionDatabaseKeyCiphertext)),
                emojiStatusDocumentId = emojiStatusDocumentId,
            )
        } catch (exception: RuntimeException) {
            connection.lastSyncErrorCode = "TELEGRAM_SYNC_FAILED"
            connection.lastSyncErrorMessage = exception.message ?: "Telegram sync failed."
            accountConnectionRepository.save(connection)
            throw TelegramSyncFailedException(cause = exception)
        }

        connection.activeModeId = nextActiveMode?.id
        connection.lastSyncErrorCode = null
        connection.lastSyncErrorMessage = null
        connection.lastSyncedAt = now
        accountConnectionRepository.save(connection)
        automationTokenService.markUsed(token)

        return TelegramFocusAutomationResult(
            activeFocusMode = nextActiveMode?.modeKey,
            appliedEmojiStatusDocumentId = emojiStatusDocumentId,
        )
    }

    private fun normalizeModeKey(value: String): String =
        value.trim()
            .takeIf { it.isNotBlank() && it.length <= 64 }
            ?: throw TelegramModeInvalidException("Telegram mode must be a non-blank string up to 64 characters.")
}
