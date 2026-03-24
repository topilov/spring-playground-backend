package me.topilov.springplayground.profile.dto

import jakarta.validation.constraints.NotBlank

data class VerifyEmailChangeRequest(
    @field:NotBlank
    val token: String,
)
