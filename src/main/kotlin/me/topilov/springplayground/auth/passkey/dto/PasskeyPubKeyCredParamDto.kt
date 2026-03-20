package me.topilov.springplayground.auth.passkey.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Supported public key credential parameter.")
data class PasskeyPubKeyCredParamDto(
    @field:Schema(example = "public-key")
    val type: String,
    @field:Schema(example = "-7")
    val alg: Long,
)
