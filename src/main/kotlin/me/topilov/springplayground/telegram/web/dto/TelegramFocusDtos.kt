package me.topilov.springplayground.telegram.web.dto

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.validation.constraints.NotBlank

data class TelegramFocusSettingsRequest(
    val defaultEmojiStatusDocumentId: String? = null,
)

data class TelegramFocusUpdateRequest(
    @field:NotBlank
    val mode: String,
    val active: Boolean,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class TelegramFocusUpdateResponse(
    val applied: Boolean,
    val activeFocusMode: String? = null,
    val appliedEmojiStatusDocumentId: String? = null,
)
