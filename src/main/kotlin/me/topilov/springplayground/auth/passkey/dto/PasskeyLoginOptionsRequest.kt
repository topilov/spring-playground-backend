package me.topilov.springplayground.auth.passkey.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Passkey login options request.")
data class PasskeyLoginOptionsRequest(
    @field:Schema(description = "Reserved for future login hints.")
    val usernameOrEmail: String? = null,
    val captchaToken: String? = null,
)
