package me.topilov.springplayground.protection.exception

class RateLimitExceededException(
    val code: String,
    val retryAfterSeconds: Long,
    message: String,
) : RuntimeException(message)
