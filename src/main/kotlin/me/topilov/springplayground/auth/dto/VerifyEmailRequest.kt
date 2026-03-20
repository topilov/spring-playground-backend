package me.topilov.springplayground.auth.dto

import jakarta.validation.constraints.NotBlank

data class VerifyEmailRequest(
    @field:NotBlank
    val token: String,
)
