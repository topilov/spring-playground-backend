package me.topilov.springplayground.profile.dto

data class ProfileResponse(
    val id: Long,
    val userId: Long,
    val username: String,
    val email: String,
    val role: String,
    val displayName: String,
    val bio: String,
)
