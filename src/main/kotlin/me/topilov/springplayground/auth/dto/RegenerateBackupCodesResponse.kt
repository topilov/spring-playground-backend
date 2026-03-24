package me.topilov.springplayground.auth.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Fresh backup codes returned after regeneration.")
data class RegenerateBackupCodesResponse(
    val backupCodes: List<String>,
)
