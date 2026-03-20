package me.topilov.springplayground.auth.passkey.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "WebAuthn public key credential request options.")
data class PasskeyAuthenticationPublicKeyOptionsDto(
    @field:Schema(example = "base64url-challenge")
    val challenge: String,
    val timeout: Long? = null,
    val rpId: String? = null,
    val allowCredentials: List<PasskeyCredentialDescriptorDto> = emptyList(),
    val userVerification: String? = null,
    val hints: List<String> = emptyList(),
    val extensions: Map<String, Any?> = emptyMap(),
)
