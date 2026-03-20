package me.topilov.springplayground.auth.passkey.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "WebAuthn public key credential creation options.")
data class PasskeyRegistrationPublicKeyOptionsDto(
    @field:Schema(example = "base64url-challenge")
    val challenge: String,
    val rp: PasskeyRelyingPartyDto,
    val user: PasskeyUserIdentityDto,
    val pubKeyCredParams: List<PasskeyPubKeyCredParamDto>,
    val timeout: Long? = null,
    val excludeCredentials: List<PasskeyCredentialDescriptorDto> = emptyList(),
    val authenticatorSelection: Map<String, Any?>? = null,
    val attestation: String? = null,
    val hints: List<String> = emptyList(),
    val extensions: Map<String, Any?> = emptyMap(),
)
