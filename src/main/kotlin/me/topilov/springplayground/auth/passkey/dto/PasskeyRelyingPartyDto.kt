package me.topilov.springplayground.auth.passkey.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "WebAuthn relying party information.")
data class PasskeyRelyingPartyDto(
    @field:Schema(example = "Spring Playground")
    val name: String,
    @field:Schema(example = "localhost")
    val id: String? = null,
)
