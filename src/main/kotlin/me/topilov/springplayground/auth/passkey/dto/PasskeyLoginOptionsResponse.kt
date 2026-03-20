package me.topilov.springplayground.auth.passkey.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Public key credential request options for passkey login.")
data class PasskeyLoginOptionsResponse(
    @field:Schema(example = "ceremony-id")
    val ceremonyId: String,
    val publicKey: Map<String, Any?>,
)
