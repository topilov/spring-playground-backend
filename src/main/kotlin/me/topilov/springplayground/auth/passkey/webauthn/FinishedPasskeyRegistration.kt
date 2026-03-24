package me.topilov.springplayground.auth.passkey.webauthn

import java.util.UUID

data class FinishedPasskeyRegistration(
    val credentialId: String,
    val publicKeyCose: ByteArray,
    val signatureCount: Long,
    val aaguid: UUID? = null,
    val transports: List<String> = emptyList(),
    val authenticatorAttachment: String? = null,
    val discoverable: Boolean? = null,
    val backupEligible: Boolean? = null,
    val backupState: Boolean? = null,
)
