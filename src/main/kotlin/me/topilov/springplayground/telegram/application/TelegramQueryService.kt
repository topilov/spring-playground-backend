package me.topilov.springplayground.telegram.application

import me.topilov.springplayground.telegram.domain.TelegramConnectionStatus
import me.topilov.springplayground.telegram.domain.TelegramEmojiMappingResolver
import me.topilov.springplayground.telegram.domain.TelegramFocusPriorityResolver
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramAccountConnectionRepository
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramAutomationTokenRepository
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramFocusMappingRepository
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramFocusStateRepository
import me.topilov.springplayground.telegram.web.dto.TelegramAutomationTokenSummaryResponse
import me.topilov.springplayground.telegram.web.dto.TelegramSettingsResponse
import me.topilov.springplayground.telegram.web.dto.TelegramUserSummaryResponse
import org.springframework.stereotype.Service

@Service
class TelegramQueryService(
    private val accountConnectionRepository: TelegramAccountConnectionRepository,
    private val automationTokenRepository: TelegramAutomationTokenRepository,
    private val focusMappingRepository: TelegramFocusMappingRepository,
    private val focusStateRepository: TelegramFocusStateRepository,
    private val priorityResolver: TelegramFocusPriorityResolver,
    private val mappingResolver: TelegramEmojiMappingResolver,
) {
    fun getSettings(userId: Long): TelegramSettingsResponse {
        val connection = accountConnectionRepository.findByUserId(userId).orElse(null)
        val userMappings = focusMappingRepository.findAllByUserId(userId).associate { it.focusMode to it.emojiStatusDocumentId }
        val focusStates = focusStateRepository.findAllByUserId(userId)
        val effectiveMode = priorityResolver.resolveEffectiveMode(focusStates)?.focusMode
        val token = automationTokenRepository.findByUserIdAndRevokedAtIsNull(userId)

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
            effectiveFocusMode = effectiveMode,
            resolvedEmojiMappings = mappingResolver.resolveMappings(userMappings).mapKeys { it.key.value },
            activeFocusModes = focusStates.filter { it.active }.map { it.focusMode },
        )
    }
}
