package me.topilov.springplayground.auth.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ResetPasswordRequest(
    @field:NotBlank
    val token: String,
    @field:NotBlank
    @field:Size(min = 8, max = 100)
    val newPassword: String,
    val captchaToken: String? = null,
)
