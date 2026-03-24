package me.topilov.springplayground.auth.passkey.webauthn

data class PasskeyUserIdentity(
    val username: String,
    val displayName: String,
    val userHandle: String,
)
