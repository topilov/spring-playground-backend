package me.topilov.springplayground.telegram.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import me.topilov.springplayground.telegram.application.TelegramFocusAutomationApplicationService
import me.topilov.springplayground.telegram.web.dto.TelegramFocusUpdateRequest
import me.topilov.springplayground.telegram.web.dto.TelegramFocusUpdateResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/telegram")
class TelegramAutomationController(
    private val focusAutomationApplicationService: TelegramFocusAutomationApplicationService,
) {
    @PostMapping("/focus-updates")
    fun applyFocusUpdate(
        @RequestHeader("Authorization") authorization: String,
        @Valid @RequestBody request: TelegramFocusUpdateRequest,
        servletRequest: HttpServletRequest,
    ): TelegramFocusUpdateResponse {
        val result = focusAutomationApplicationService.applyFocusUpdate(
            rawToken = authorization.removePrefix("Bearer ").trim(),
            mode = request.mode,
            active = request.active,
            request = servletRequest,
        )
        return TelegramFocusUpdateResponse(
            applied = true,
            activeFocusMode = result.activeFocusMode,
            appliedEmojiStatusDocumentId = result.appliedEmojiStatusDocumentId,
        )
    }
}
