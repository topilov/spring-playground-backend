package me.topilov.springplayground.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

@Schema(description = "TOTP code submitted to complete a pending two-factor login challenge.")
data class TwoFactorLoginVerifyRequest(
    @field:NotBlank
    val loginChallengeId: String,
    @field:NotBlank
    @field:Pattern(regexp = "^\\d{6}$")
    val code: String,
)
