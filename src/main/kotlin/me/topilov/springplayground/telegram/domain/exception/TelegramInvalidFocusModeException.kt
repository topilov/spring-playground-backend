package me.topilov.springplayground.telegram.domain.exception

class TelegramInvalidFocusModeException(
    value: String,
) : RuntimeException("Unsupported telegram focus mode: $value")
