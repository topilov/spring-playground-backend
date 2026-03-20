package me.topilov.springplayground.auth.passkey.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

@Schema(description = "Browser passkey assertion forwarded to the backend for login.")
data class PasskeyLoginVerifyRequest(
    @field:NotBlank
    @field:Schema(example = "ceremony-id")
    val ceremonyId: String,
    @field:NotNull
    val credential: Map<String, Any?>,
)
