package me.topilov.springplayground.telegram.web.dto

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.validation.constraints.NotBlank
import me.topilov.springplayground.telegram.application.TelegramPendingAuthStep
import me.topilov.springplayground.telegram.domain.TelegramConnectionStatus

data class TelegramConnectStartRequest(
    @field:NotBlank
    val phoneNumber: String,
)

data class TelegramConnectCodeRequest(
    @field:NotBlank
    val pendingAuthId: String,
    @field:NotBlank
    val code: String,
)

data class TelegramConnectPasswordRequest(
    @field:NotBlank
    val pendingAuthId: String,
    @field:NotBlank
    val password: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class TelegramConnectResponse(
    val connected: Boolean,
    val pendingAuthId: String? = null,
    val nextStep: TelegramPendingAuthStep? = null,
    val connectionStatus: TelegramConnectionStatus? = null,
    val telegramUser: TelegramUserSummaryResponse? = null,
)
