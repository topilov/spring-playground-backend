package me.topilov.springplayground.common.web

data class ErrorResponse(
    val error: String,
    val code: String? = null,
    val retryAfterSeconds: Long? = null,
)
