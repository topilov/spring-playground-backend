package me.topilov.springplayground.telegram.infrastructure.crypto

interface TelegramSessionSecretCrypto {
    fun encrypt(secret: String): String

    fun decrypt(ciphertext: String): String
}
