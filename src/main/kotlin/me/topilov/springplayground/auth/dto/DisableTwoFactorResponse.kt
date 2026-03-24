package me.topilov.springplayground.auth.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Result of disabling TOTP two-factor authentication.")
data class DisableTwoFactorResponse(
    val disabled: Boolean = true,
)
