package me.topilov.springplayground.telegram.web

import jakarta.validation.Valid
import me.topilov.springplayground.auth.security.AppUserPrincipal
import me.topilov.springplayground.telegram.application.TelegramAutomationTokenApplicationService
import me.topilov.springplayground.telegram.application.TelegramConnectionApplicationService
import me.topilov.springplayground.telegram.application.TelegramFocusSettingsService
import me.topilov.springplayground.telegram.application.TelegramModeApplicationService
import me.topilov.springplayground.telegram.application.TelegramQueryService
import me.topilov.springplayground.telegram.web.dto.TelegramAutomationTokenResponse
import me.topilov.springplayground.telegram.web.dto.TelegramConnectCodeRequest
import me.topilov.springplayground.telegram.web.dto.TelegramConnectPasswordRequest
import me.topilov.springplayground.telegram.web.dto.TelegramConnectResponse
import me.topilov.springplayground.telegram.web.dto.TelegramConnectStartRequest
import me.topilov.springplayground.telegram.web.dto.TelegramCreateModeRequest
import me.topilov.springplayground.telegram.web.dto.TelegramFocusSettingsRequest
import me.topilov.springplayground.telegram.web.dto.TelegramModeListResponse
import me.topilov.springplayground.telegram.web.dto.TelegramModeResponse
import me.topilov.springplayground.telegram.web.dto.TelegramSettingsResponse
import me.topilov.springplayground.telegram.web.dto.TelegramUpdateModeRequest
import me.topilov.springplayground.telegram.web.dto.TelegramUserSummaryResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/profile/me/telegram")
class TelegramController(
    private val queryService: TelegramQueryService,
    private val connectionApplicationService: TelegramConnectionApplicationService,
    private val focusSettingsService: TelegramFocusSettingsService,
    private val automationTokenApplicationService: TelegramAutomationTokenApplicationService,
    private val modeApplicationService: TelegramModeApplicationService,
) {
    @GetMapping
    fun settings(@AuthenticationPrincipal principal: AppUserPrincipal): TelegramSettingsResponse =
        queryService.getSettings(principal.id)

    @PostMapping("/connect/start")
    fun startConnection(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @Valid @RequestBody request: TelegramConnectStartRequest,
    ): TelegramConnectResponse {
        val result = connectionApplicationService.startConnection(principal.id, request.phoneNumber)
        return TelegramConnectResponse(
            connected = result.connected,
            pendingAuthId = result.pendingAuthId,
            nextStep = result.nextStep,
        )
    }

    @PostMapping("/connect/confirm-code")
    fun confirmCode(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @Valid @RequestBody request: TelegramConnectCodeRequest,
    ): TelegramConnectResponse {
        val result = connectionApplicationService.confirmCode(principal.id, request.pendingAuthId, request.code)
        return TelegramConnectResponse(
            connected = result.connected,
            pendingAuthId = result.pendingAuthId,
            nextStep = result.nextStep,
            connectionStatus = result.connectionStatus,
            telegramUser = result.connection?.telegramUserId?.let {
                TelegramUserSummaryResponse(
                    id = it,
                    phoneNumber = result.connection.telegramPhoneNumber.orEmpty(),
                    username = result.connection.telegramUsername,
                    displayName = result.connection.telegramDisplayName.orEmpty(),
                    premium = result.connection.premium,
                )
            },
        )
    }

    @PostMapping("/connect/confirm-password")
    fun confirmPassword(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @Valid @RequestBody request: TelegramConnectPasswordRequest,
    ): TelegramConnectResponse {
        val result = connectionApplicationService.confirmPassword(principal.id, request.pendingAuthId, request.password)
        return TelegramConnectResponse(
            connected = result.connected,
            connectionStatus = result.connectionStatus,
            telegramUser = result.connection?.telegramUserId?.let {
                TelegramUserSummaryResponse(
                    id = it,
                    phoneNumber = result.connection.telegramPhoneNumber.orEmpty(),
                    username = result.connection.telegramUsername,
                    displayName = result.connection.telegramDisplayName.orEmpty(),
                    premium = result.connection.premium,
                )
            },
        )
    }

    @DeleteMapping("/connect")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun disconnect(@AuthenticationPrincipal principal: AppUserPrincipal) {
        connectionApplicationService.disconnect(principal.id)
    }

    @PutMapping("/focus-settings")
    fun updateFocusSettings(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @Valid @RequestBody request: TelegramFocusSettingsRequest,
    ): TelegramSettingsResponse {
        focusSettingsService.updateSettings(
            userId = principal.id,
            defaultEmojiStatusDocumentId = request.defaultEmojiStatusDocumentId,
        )
        return queryService.getSettings(principal.id)
    }

    @GetMapping("/modes")
    fun listModes(
        @AuthenticationPrincipal principal: AppUserPrincipal,
    ): TelegramModeListResponse = TelegramModeListResponse(
        modes = modeApplicationService.listModes(principal.id).map(::toModeResponse),
    )

    @PostMapping("/modes")
    @ResponseStatus(HttpStatus.CREATED)
    fun createMode(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @Valid @RequestBody request: TelegramCreateModeRequest,
    ): TelegramModeResponse = toModeResponse(
        modeApplicationService.createMode(principal.id, request.mode, request.emojiStatusDocumentId),
    )

    @PatchMapping("/modes/{mode}")
    fun updateMode(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable mode: String,
        @Valid @RequestBody request: TelegramUpdateModeRequest,
    ): TelegramModeResponse = toModeResponse(
        modeApplicationService.updateMode(
            userId = principal.id,
            mode = mode,
            newMode = request.newMode,
            emojiStatusDocumentId = request.emojiStatusDocumentId,
        ),
    )

    @DeleteMapping("/modes/{mode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMode(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable mode: String,
    ) {
        modeApplicationService.deleteMode(principal.id, mode)
    }

    @PostMapping("/automation-token")
    fun createAutomationToken(
        @AuthenticationPrincipal principal: AppUserPrincipal,
    ): TelegramAutomationTokenResponse {
        val token = automationTokenApplicationService.createToken(principal.id)
        return TelegramAutomationTokenResponse(token = token.token, tokenHint = token.tokenHint)
    }

    @PostMapping("/automation-token/regenerate")
    fun regenerateAutomationToken(
        @AuthenticationPrincipal principal: AppUserPrincipal,
    ): TelegramAutomationTokenResponse {
        val token = automationTokenApplicationService.regenerateToken(principal.id)
        return TelegramAutomationTokenResponse(token = token.token, tokenHint = token.tokenHint)
    }

    @DeleteMapping("/automation-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeAutomationToken(@AuthenticationPrincipal principal: AppUserPrincipal) {
        automationTokenApplicationService.revokeToken(principal.id)
    }

    private fun toModeResponse(mode: me.topilov.springplayground.telegram.domain.TelegramMode): TelegramModeResponse =
        TelegramModeResponse(
            mode = mode.modeKey,
            emojiStatusDocumentId = mode.emojiStatusDocumentId,
        )
}
