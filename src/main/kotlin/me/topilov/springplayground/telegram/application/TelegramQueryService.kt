package me.topilov.springplayground.telegram.application

import me.topilov.springplayground.telegram.domain.TelegramConnectionStatus
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramAccountConnectionRepository
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramAutomationTokenRepository
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramModeRepository
import me.topilov.springplayground.telegram.web.dto.TelegramAutomationTokenSummaryResponse
import me.topilov.springplayground.telegram.web.dto.TelegramModeResponse
import me.topilov.springplayground.telegram.web.dto.TelegramSettingsResponse
import me.topilov.springplayground.telegram.web.dto.TelegramUserSummaryResponse
import org.springframework.stereotype.Service

@Service
class TelegramQueryService(
    private val accountConnectionRepository: TelegramAccountConnectionRepository,
    private val automationTokenRepository: TelegramAutomationTokenRepository,
    private val modeRepository: TelegramModeRepository,
) {
    fun getSettings(userId: Long): TelegramSettingsResponse {
        val connection = accountConnectionRepository.findByUserId(userId).orElse(null)
        val modes = modeRepository.findAllByUserIdOrderByModeKeyAsc(userId)
        val token = automationTokenRepository.findByUserIdAndRevokedAtIsNull(userId)
        val activeMode = modes.firstOrNull { it.id == connection?.activeModeId }?.modeKey

        return TelegramSettingsResponse(
            connected = connection?.connectionStatus == TelegramConnectionStatus.CONNECTED,
            connectionStatus = connection?.connectionStatus,
            telegramUser = if (connection?.connectionStatus == TelegramConnectionStatus.CONNECTED && connection.telegramUserId != null) {
                TelegramUserSummaryResponse(
                    id = connection.telegramUserId!!,
                    phoneNumber = connection.telegramPhoneNumber.orEmpty(),
                    username = connection.telegramUsername,
                    displayName = connection.telegramDisplayName.orEmpty(),
                    premium = connection.premium,
                )
            } else {
                null
            },
            pendingAuth = null,
            automationToken = TelegramAutomationTokenSummaryResponse(
                present = token != null,
                tokenHint = token?.tokenHint,
                createdAt = token?.createdAt,
                lastUsedAt = token?.lastUsedAt,
            ),
            defaultEmojiStatusDocumentId = connection?.defaultEmojiStatusDocumentId,
            activeFocusMode = activeMode,
            modes = modes.map {
                TelegramModeResponse(
                    mode = it.modeKey,
                    emojiStatusDocumentId = it.emojiStatusDocumentId,
                )
            },
        )
    }
}
