package me.topilov.springplayground.auth.passkey.webauthn

import java.util.UUID

interface PasskeyWebAuthnService {
    fun beginRegistration(user: PasskeyUserIdentity, existingCredentialIds: List<String>): StartedPasskeyRegistration

    fun finishRegistration(requestJson: String, credentialJson: String): FinishedPasskeyRegistration

    fun beginAuthentication(): StartedPasskeyAuthentication

    fun finishAuthentication(requestJson: String, credentialJson: String): FinishedPasskeyAuthentication
}

data class PasskeyUserIdentity(
    val username: String,
    val displayName: String,
    val userHandle: String,
)

data class StartedPasskeyRegistration(
    val requestJson: String,
    val credentialCreationJson: String,
)

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

data class StartedPasskeyAuthentication(
    val requestJson: String,
    val credentialRequestJson: String,
)

data class FinishedPasskeyAuthentication(
    val credentialId: String,
    val signatureCount: Long,
    val backupEligible: Boolean? = null,
    val backupState: Boolean? = null,
)
