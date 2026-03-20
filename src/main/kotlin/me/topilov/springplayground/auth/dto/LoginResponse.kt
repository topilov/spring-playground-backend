package me.topilov.springplayground.auth.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Session login result returned after successful authentication.")
data class LoginResponse(
    @field:Schema(example = "true")
    val authenticated: Boolean = true,
    @field:Schema(example = "1")
    val userId: Long,
    @field:Schema(example = "demo")
    val username: String,
    @field:Schema(example = "demo@example.com")
    val email: String,
    @field:Schema(example = "USER")
    val role: String,
)
