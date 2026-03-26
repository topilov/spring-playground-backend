package me.topilov.springplayground.telegram.domain

class TelegramEmojiMappingResolver(
    private val defaults: Map<TelegramFocusMode, String> = emptyMap(),
) {
    fun resolveEmojiStatusDocumentId(
        effectiveMode: TelegramFocusMode?,
        userMappings: Map<TelegramFocusMode, String>,
        defaultNoFocusEmojiStatusDocumentId: String?,
    ): String? {
        if (effectiveMode == null) {
            return defaultNoFocusEmojiStatusDocumentId
        }
        return userMappings[effectiveMode] ?: defaults[effectiveMode]
    }

    fun resolveMappings(userMappings: Map<TelegramFocusMode, String>): Map<TelegramFocusMode, String?> =
        TelegramFocusMode.entries.associateWith { userMappings[it] ?: defaults[it] }
}
