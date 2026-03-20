package me.topilov.springplayground.auth.passkey.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "WebAuthn user identity information for registration.")
data class PasskeyUserIdentityDto(
    @field:Schema(example = "base64url-user-id")
    val id: String,
    @field:Schema(example = "demo")
    val name: String,
    @field:Schema(example = "Demo User")
    val displayName: String,
)
