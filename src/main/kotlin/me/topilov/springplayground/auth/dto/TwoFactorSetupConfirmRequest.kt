package me.topilov.springplayground.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

@Schema(description = "TOTP confirmation code used to enable two-factor authentication.")
data class TwoFactorSetupConfirmRequest(
    @field:NotBlank
    @field:Pattern(regexp = "^\\d{6}$")
    val code: String,
)
