package me.topilov.springplayground.auth.passkey.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Credential descriptor used in WebAuthn option payloads.")
data class PasskeyCredentialDescriptorDto(
    @field:Schema(example = "public-key")
    val type: String,
    @field:Schema(example = "base64url-credential-id")
    val id: String,
    val transports: List<String> = emptyList(),
)
