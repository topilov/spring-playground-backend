package me.topilov.springplayground.auth.passkey.webauthn

data class StartedPasskeyRegistration(
    val requestJson: String,
    val credentialCreationJson: String,
)
