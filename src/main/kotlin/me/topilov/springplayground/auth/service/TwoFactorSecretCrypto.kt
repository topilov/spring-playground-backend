package me.topilov.springplayground.auth.service

interface TwoFactorSecretCrypto {
    fun encrypt(secret: String): String
    fun decrypt(ciphertext: String): String
}
