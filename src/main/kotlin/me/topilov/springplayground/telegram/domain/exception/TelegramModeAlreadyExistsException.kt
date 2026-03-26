package me.topilov.springplayground.telegram.domain.exception

class TelegramModeAlreadyExistsException(
    mode: String,
) : RuntimeException("Telegram mode already exists: $mode")
