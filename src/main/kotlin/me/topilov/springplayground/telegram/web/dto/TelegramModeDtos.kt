package me.topilov.springplayground.telegram.web.dto

data class TelegramModeResponse(
    val mode: String,
    val emojiStatusDocumentId: String,
)

data class TelegramModeListResponse(
    val modes: List<TelegramModeResponse>,
)

data class TelegramCreateModeRequest(
    val mode: String,
    val emojiStatusDocumentId: String,
)

data class TelegramUpdateModeRequest(
    val newMode: String? = null,
    val emojiStatusDocumentId: String? = null,
)
