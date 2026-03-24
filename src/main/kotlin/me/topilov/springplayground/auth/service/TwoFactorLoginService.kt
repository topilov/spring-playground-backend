package me.topilov.springplayground.auth.service

import me.topilov.springplayground.auth.domain.AuthUser
import me.topilov.springplayground.auth.dto.TwoFactorLoginChallengeResponse
import me.topilov.springplayground.auth.exception.TwoFactorAuthenticationFailedException
import me.topilov.springplayground.auth.exception.TwoFactorLoginChallengeNotFoundException
import me.topilov.springplayground.auth.repository.AuthTotpCredentialRepository
import me.topilov.springplayground.auth.repository.AuthUserRepository
import me.topilov.springplayground.config.TwoFactorProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class TwoFactorLoginService(
    private val challengeStore: TwoFactorLoginChallengeStore,
    private val credentialRepository: AuthTotpCredentialRepository,
    private val authUserRepository: AuthUserRepository,
    private val secretCrypto: TwoFactorSecretCrypto,
    private val totpCodeService: TotpCodeService,
    private val backupCodeService: BackupCodeService,
    private val twoFactorProperties: TwoFactorProperties,
) {
    @Transactional(readOnly = true)
    fun isEnabledForUser(userId: Long): Boolean = credentialRepository.existsByUserIdAndEnabledAtIsNotNull(userId)

    fun createLoginChallenge(userId: Long): TwoFactorLoginChallengeResponse {
        val challengeId = challengeStore.createChallengeId()
        val expiresAt = Instant.now().plus(twoFactorProperties.loginChallengeTtl)
        challengeStore.save(
            StoredTwoFactorLoginChallenge(
                loginChallengeId = challengeId,
                userId = userId,
                expiresAt = expiresAt,
            ),
        )

        return TwoFactorLoginChallengeResponse(
            loginChallengeId = challengeId,
            methods = listOf("TOTP", "BACKUP_CODE"),
            expiresAt = expiresAt,
        )
    }

    @Transactional
    fun previewChallenge(loginChallengeId: String): StoredTwoFactorLoginChallenge =
        challengeStore.find(loginChallengeId) ?: throw TwoFactorLoginChallengeNotFoundException()

    @Transactional
    fun completeTotpChallenge(loginChallengeId: String, code: String): AuthUser {
        val challenge = consumeChallenge(loginChallengeId)
        val credential = credentialRepository.findByUserIdAndEnabledAtIsNotNull(challenge.userId)
            .orElseThrow(::TwoFactorAuthenticationFailedException)
        val secret = secretCrypto.decrypt(credential.secretCiphertext)
        if (!totpCodeService.verify(secret, code)) {
            throw TwoFactorAuthenticationFailedException()
        }
        return authUserRepository.findById(challenge.userId).orElseThrow(::TwoFactorAuthenticationFailedException)
    }

    @Transactional
    fun completeBackupCodeChallenge(loginChallengeId: String, backupCode: String): AuthUser {
        val challenge = consumeChallenge(loginChallengeId)
        val credential = credentialRepository.findByUserIdAndEnabledAtIsNotNull(challenge.userId)
            .orElseThrow(::TwoFactorAuthenticationFailedException)
        if (!backupCodeService.consumeCode(credential, backupCode)) {
            throw TwoFactorAuthenticationFailedException()
        }
        return authUserRepository.findById(challenge.userId).orElseThrow(::TwoFactorAuthenticationFailedException)
    }

    private fun consumeChallenge(loginChallengeId: String): StoredTwoFactorLoginChallenge =
        challengeStore.consume(loginChallengeId) ?: throw TwoFactorLoginChallengeNotFoundException()
}
