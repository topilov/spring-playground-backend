package me.topilov.springplayground.telegram.domain.exception

class TelegramSyncFailedException(
    message: String = "Telegram emoji status sync failed.",
    cause: Throwable? = null,
) : RuntimeException(message, cause)
