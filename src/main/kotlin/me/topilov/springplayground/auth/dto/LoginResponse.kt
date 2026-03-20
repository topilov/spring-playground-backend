package me.topilov.springplayground.auth.dto

data class LoginResponse(
    val authenticated: Boolean = true,
    val userId: Long,
    val username: String,
    val email: String,
    val role: String,
)
