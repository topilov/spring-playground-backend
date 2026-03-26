package me.topilov.springplayground.telegram.web.dto

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.validation.constraints.NotNull
import me.topilov.springplayground.telegram.domain.TelegramFocusMode

data class TelegramFocusSettingsRequest(
    val defaultEmojiStatusDocumentId: String? = null,
    val mappings: Map<String, String?>? = emptyMap(),
)

data class TelegramFocusUpdateRequest(
    @field:NotNull
    val mode: TelegramFocusMode,
    val active: Boolean,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class TelegramFocusUpdateResponse(
    val applied: Boolean,
    val effectiveFocusMode: TelegramFocusMode? = null,
    val appliedEmojiStatusDocumentId: String? = null,
)
