package me.topilov.springplayground.auth.passkey.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Public key credential creation options for authenticated passkey registration.")
data class PasskeyRegistrationOptionsResponse(
    @field:Schema(example = "ceremony-id")
    val ceremonyId: String,
    val publicKey: Map<String, Any?>,
)
