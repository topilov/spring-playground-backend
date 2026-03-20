package me.topilov.springplayground.auth.dto

data class RegisterResponse(
    val userId: Long,
    val username: String,
    val email: String,
)
