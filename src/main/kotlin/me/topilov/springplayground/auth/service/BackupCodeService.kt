package me.topilov.springplayground.auth.service

import me.topilov.springplayground.auth.domain.AuthTotpBackupCode
import me.topilov.springplayground.auth.domain.AuthTotpCredential
import me.topilov.springplayground.auth.repository.AuthTotpBackupCodeRepository
import me.topilov.springplayground.config.TwoFactorProperties
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Instant

@Component
class BackupCodeService(
    private val passwordEncoder: PasswordEncoder,
    private val backupCodeRepository: AuthTotpBackupCodeRepository,
    private val twoFactorProperties: TwoFactorProperties,
) {
    fun replaceCodes(credential: AuthTotpCredential): List<String> {
        val credentialId = requireNotNull(credential.id) { "Persisted TOTP credential id is missing" }
        backupCodeRepository.deleteAllByCredentialId(credentialId)

        val plainCodes = (1..twoFactorProperties.backupCodeCount).map { generateFormattedCode() }
        val entities = plainCodes.map { plainCode ->
            AuthTotpBackupCode(
                credential = credential,
                codeHash = requireNotNull(passwordEncoder.encode(normalize(plainCode))) {
                    "Encoded backup code hash is missing"
                },
            )
        }
        backupCodeRepository.saveAll(entities)
        return plainCodes
    }

    fun consumeCode(credential: AuthTotpCredential, rawCode: String): Boolean {
        val credentialId = requireNotNull(credential.id) { "Persisted TOTP credential id is missing" }
        val normalizedInput = normalize(rawCode)
        if (normalizedInput.isBlank()) {
            return false
        }

        val candidate = backupCodeRepository.findAllByCredentialIdAndUsedAtIsNullOrderByIdAsc(credentialId)
            .firstOrNull { passwordEncoder.matches(normalizedInput, it.codeHash) }
            ?: return false

        return backupCodeRepository.markUsed(
            id = requireNotNull(candidate.id) { "Persisted backup code id is missing" },
            usedAt = Instant.now(),
        ) == 1
    }

    fun remainingCount(credentialId: Long): Int = backupCodeRepository.countByCredentialIdAndUsedAtIsNull(credentialId).toInt()

    private fun generateFormattedCode(): String {
        val raw = buildString {
            repeat(12) {
                append(ALPHABET[secureRandom.nextInt(ALPHABET.length)])
            }
        }
        return raw.chunked(4).joinToString("-")
    }

    private fun normalize(rawCode: String): String =
        rawCode.uppercase().filter(Char::isLetterOrDigit)

    companion object {
        private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        private val secureRandom = SecureRandom()
    }
}
