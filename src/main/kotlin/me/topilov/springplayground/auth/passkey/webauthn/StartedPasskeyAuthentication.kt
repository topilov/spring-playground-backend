package me.topilov.springplayground.auth.passkey.webauthn

data class StartedPasskeyAuthentication(
    val requestJson: String,
    val credentialRequestJson: String,
)
