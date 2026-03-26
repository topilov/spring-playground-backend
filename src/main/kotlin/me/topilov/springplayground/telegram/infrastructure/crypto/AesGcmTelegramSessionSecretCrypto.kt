package me.topilov.springplayground.telegram.infrastructure.crypto

import me.topilov.springplayground.telegram.infrastructure.config.TelegramProperties
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class AesGcmTelegramSessionSecretCrypto(
    properties: TelegramProperties,
) : TelegramSessionSecretCrypto {
    private val secretKey = SecretKeySpec(Base64.getDecoder().decode(properties.encryptionKeyBase64.trim()), "AES")

    override fun encrypt(secret: String): String {
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    override fun decrypt(ciphertext: String): String {
        val payload = Base64.getDecoder().decode(ciphertext.trim())
        require(payload.size > 12) { "Encrypted telegram session secret payload is invalid" }
        val iv = payload.copyOfRange(0, 12)
        val encrypted = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    companion object {
        private val secureRandom = SecureRandom()
    }
}
