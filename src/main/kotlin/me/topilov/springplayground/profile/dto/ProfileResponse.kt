package me.topilov.springplayground.profile.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Current authenticated user's profile.")
data class ProfileResponse(
    @field:Schema(example = "1")
    val id: Long,
    @field:Schema(example = "1")
    val userId: Long,
    @field:Schema(example = "demo")
    val username: String,
    @field:Schema(example = "demo@example.com")
    val email: String,
    @field:Schema(example = "USER")
    val role: String,
    @field:Schema(example = "Demo User")
    val displayName: String,
    @field:Schema(example = "Session-backed example profile")
    val bio: String,
)
