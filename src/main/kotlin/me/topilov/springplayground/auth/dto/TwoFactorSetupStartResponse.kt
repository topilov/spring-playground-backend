package me.topilov.springplayground.auth.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "TOTP setup payload returned before confirmation.")
data class TwoFactorSetupStartResponse(
    val secret: String,
    val otpauthUri: String,
)
