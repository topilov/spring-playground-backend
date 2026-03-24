package me.topilov.springplayground.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "Backup code submitted to complete a pending two-factor login challenge.")
data class TwoFactorBackupCodeLoginVerifyRequest(
    @field:NotBlank
    val loginChallengeId: String,
    @field:NotBlank
    val backupCode: String,
    val captchaToken: String? = null,
)
