package me.topilov.springplayground.auth.passkey.webauthn

data class FinishedPasskeyAuthentication(
    val credentialId: String,
    val signatureCount: Long,
    val backupEligible: Boolean? = null,
    val backupState: Boolean? = null,
)
