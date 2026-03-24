package me.topilov.springplayground.auth.passkey.webauthn

interface PasskeyWebAuthnService {
    fun beginRegistration(user: PasskeyUserIdentity, existingCredentialIds: List<String>): StartedPasskeyRegistration

    fun finishRegistration(requestJson: String, credentialJson: String): FinishedPasskeyRegistration

    fun beginAuthentication(): StartedPasskeyAuthentication

    fun finishAuthentication(requestJson: String, credentialJson: String): FinishedPasskeyAuthentication
}
