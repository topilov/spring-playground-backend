package me.topilov.springplayground.telegram.web.dto

import com.fasterxml.jackson.annotation.JsonInclude
import me.topilov.springplayground.telegram.application.TelegramPendingAuthStep
import me.topilov.springplayground.telegram.domain.TelegramConnectionStatus
import java.time.Instant

data class TelegramUserSummaryResponse(
    val id: Long,
    val phoneNumber: String,
    val username: String?,
    val displayName: String,
    val premium: Boolean,
)

data class TelegramPendingAuthResponse(
    val pendingAuthId: String,
    val nextStep: TelegramPendingAuthStep,
)

data class TelegramAutomationTokenSummaryResponse(
    val present: Boolean,
    val tokenHint: String? = null,
    val createdAt: Instant? = null,
    val lastUsedAt: Instant? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class TelegramSettingsResponse(
    val connected: Boolean,
    val connectionStatus: TelegramConnectionStatus? = null,
    val telegramUser: TelegramUserSummaryResponse? = null,
    val pendingAuth: TelegramPendingAuthResponse? = null,
    val automationToken: TelegramAutomationTokenSummaryResponse,
    val defaultEmojiStatusDocumentId: String? = null,
    val activeFocusMode: String? = null,
    val modes: List<TelegramModeResponse> = emptyList(),
)
