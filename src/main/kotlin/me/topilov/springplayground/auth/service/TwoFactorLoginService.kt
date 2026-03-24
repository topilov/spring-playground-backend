package me.topilov.springplayground.auth.service

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.topilov.springplayground.auth.dto.LoginResponse
import me.topilov.springplayground.auth.dto.TwoFactorBackupCodeLoginVerifyRequest
import me.topilov.springplayground.auth.dto.TwoFactorLoginChallengeResponse
import me.topilov.springplayground.auth.dto.TwoFactorLoginVerifyRequest
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
    private val sessionLoginService: SessionLoginService,
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
    fun completeTotpLogin(
        request: TwoFactorLoginVerifyRequest,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): LoginResponse {
        val challenge = consumeChallenge(request.loginChallengeId)
        val credential = credentialRepository.findByUserIdAndEnabledAtIsNotNull(challenge.userId)
            .orElseThrow(::TwoFactorAuthenticationFailedException)
        val secret = secretCrypto.decrypt(credential.secretCiphertext)
        if (!totpCodeService.verify(secret, request.code)) {
            throw TwoFactorAuthenticationFailedException()
        }

        val user = authUserRepository.findById(challenge.userId)
            .orElseThrow(::TwoFactorAuthenticationFailedException)
        return sessionLoginService.login(
            authentication = sessionLoginService.createAuthentication(user),
            servletRequest = servletRequest,
            servletResponse = servletResponse,
        )
    }

    @Transactional
    fun completeBackupCodeLogin(
        request: TwoFactorBackupCodeLoginVerifyRequest,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): LoginResponse {
        val challenge = consumeChallenge(request.loginChallengeId)
        val credential = credentialRepository.findByUserIdAndEnabledAtIsNotNull(challenge.userId)
            .orElseThrow(::TwoFactorAuthenticationFailedException)
        if (!backupCodeService.consumeCode(credential, request.backupCode)) {
            throw TwoFactorAuthenticationFailedException()
        }

        val user = authUserRepository.findById(challenge.userId)
            .orElseThrow(::TwoFactorAuthenticationFailedException)
        return sessionLoginService.login(
            authentication = sessionLoginService.createAuthentication(user),
            servletRequest = servletRequest,
            servletResponse = servletResponse,
        )
    }

    private fun consumeChallenge(loginChallengeId: String): StoredTwoFactorLoginChallenge =
        challengeStore.consume(loginChallengeId) ?: throw TwoFactorLoginChallengeNotFoundException()
}
