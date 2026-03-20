package me.topilov.springplayground.auth.passkey.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

@Schema(description = "Browser passkey registration result forwarded to the backend.")
data class PasskeyRegistrationVerifyRequest(
    @field:NotBlank
    @field:Schema(example = "ceremony-id")
    val ceremonyId: String,
    @field:NotNull
    val credential: Map<String, Any?>,
    @field:Schema(example = "MacBook Touch ID")
    val nickname: String? = null,
)
