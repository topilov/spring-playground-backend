package me.topilov.springplayground.auth.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Confirmation result for enabling TOTP two-factor authentication.")
data class TwoFactorSetupConfirmResponse(
    val enabled: Boolean = true,
    val backupCodes: List<String>,
)
