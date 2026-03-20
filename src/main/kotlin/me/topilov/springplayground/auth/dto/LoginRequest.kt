package me.topilov.springplayground.auth.dto

import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank
    val usernameOrEmail: String,
    @field:NotBlank
    val password: String,
)
