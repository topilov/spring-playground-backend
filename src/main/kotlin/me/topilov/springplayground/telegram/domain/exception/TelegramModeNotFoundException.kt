package me.topilov.springplayground.telegram.domain.exception

class TelegramModeNotFoundException(
    mode: String,
) : RuntimeException("Telegram mode not found: $mode")
