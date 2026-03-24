package me.topilov.springplayground.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "Current TOTP two-factor authentication status for the authenticated user.")
data class TwoFactorStatusResponse(
    val enabled: Boolean,
    val pendingSetup: Boolean,
    val backupCodesRemaining: Int,
    val enabledAt: Instant? = null,
)
