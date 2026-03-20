package me.topilov.springplayground.auth.passkey.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Authenticated passkey registration request.")
data class PasskeyRegistrationOptionsRequest(
    @field:Schema(example = "MacBook Touch ID")
    val nickname: String? = null,
)
