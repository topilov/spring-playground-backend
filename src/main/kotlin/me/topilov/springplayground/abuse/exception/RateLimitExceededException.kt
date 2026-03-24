package me.topilov.springplayground.abuse.exception

class RateLimitExceededException(
    val code: String,
    val retryAfterSeconds: Long,
    message: String,
) : RuntimeException(message)
