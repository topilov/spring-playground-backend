package me.topilov.springplayground.auth.service

import me.topilov.springplayground.config.TwoFactorProperties
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class AesGcmTwoFactorSecretCrypto(
    properties: TwoFactorProperties,
) : TwoFactorSecretCrypto {
    private val secretKey = SecretKeySpec(Base64.getDecoder().decode(properties.encryptionKeyBase64.trim()), "AES")

    init {
        require(secretKey.encoded.size in setOf(16, 24, 32)) {
            "app.two-factor.encryption-key-base64 must decode to a 128, 192, or 256 bit AES key"
        }
    }

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
        require(payload.size > 12) { "Encrypted secret payload is invalid" }
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
