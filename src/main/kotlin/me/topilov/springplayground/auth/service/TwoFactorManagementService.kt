package me.topilov.springplayground.auth.service

import jakarta.validation.Valid
import me.topilov.springplayground.auth.domain.AuthTotpCredential
import me.topilov.springplayground.auth.dto.DisableTwoFactorResponse
import me.topilov.springplayground.auth.dto.RegenerateBackupCodesResponse
import me.topilov.springplayground.auth.dto.TwoFactorSetupConfirmRequest
import me.topilov.springplayground.auth.dto.TwoFactorSetupConfirmResponse
import me.topilov.springplayground.auth.dto.TwoFactorSetupStartResponse
import me.topilov.springplayground.auth.dto.TwoFactorStatusResponse
import me.topilov.springplayground.auth.exception.AuthUserNotFoundException
import me.topilov.springplayground.auth.exception.InvalidTwoFactorCodeException
import me.topilov.springplayground.auth.exception.TwoFactorAlreadyEnabledException
import me.topilov.springplayground.auth.exception.TwoFactorNotEnabledException
import me.topilov.springplayground.auth.exception.TwoFactorSetupNotStartedException
import me.topilov.springplayground.auth.repository.AuthTotpCredentialRepository
import me.topilov.springplayground.auth.repository.AuthUserRepository
import me.topilov.springplayground.config.TwoFactorProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

@Service
class TwoFactorManagementService(
    private val authUserRepository: AuthUserRepository,
    private val credentialRepository: AuthTotpCredentialRepository,
    private val backupCodeService: BackupCodeService,
    private val totpCodeService: TotpCodeService,
    private val secretCrypto: TwoFactorSecretCrypto,
    private val twoFactorProperties: TwoFactorProperties,
) {
    @Transactional(readOnly = true)
    fun status(userId: Long): TwoFactorStatusResponse {
        val credential = credentialRepository.findByUserId(userId).orElse(null)
            ?: return TwoFactorStatusResponse(
                enabled = false,
                pendingSetup = false,
                backupCodesRemaining = 0,
            )

        val credentialId = requireNotNull(credential.id) { "Persisted TOTP credential id is missing" }
        return TwoFactorStatusResponse(
            enabled = credential.enabledAt != null,
            pendingSetup = credential.enabledAt == null,
            backupCodesRemaining = if (credential.enabledAt != null) backupCodeService.remainingCount(credentialId) else 0,
            enabledAt = credential.enabledAt,
        )
    }

    @Transactional
    fun startSetup(userId: Long): TwoFactorSetupStartResponse {
        val user = authUserRepository.findById(userId)
            .orElseThrow { AuthUserNotFoundException(userId.toString()) }
        val existing = credentialRepository.findByUserId(userId).orElse(null)
        if (existing?.enabledAt != null) {
            throw TwoFactorAlreadyEnabledException()
        }

        val secret = totpCodeService.generateSecret()
        val credential = existing ?: AuthTotpCredential(
            user = user,
            secretCiphertext = "",
        )
        credential.secretCiphertext = secretCrypto.encrypt(secret)
        credential.enabledAt = null
        credentialRepository.save(credential)

        return TwoFactorSetupStartResponse(
            secret = secret,
            otpauthUri = buildOtpauthUri(twoFactorProperties.issuer, user.email, secret),
        )
    }

    @Transactional
    fun confirmSetup(userId: Long, @Valid request: TwoFactorSetupConfirmRequest): TwoFactorSetupConfirmResponse {
        val credential = credentialRepository.findByUserId(userId).orElseThrow(::TwoFactorSetupNotStartedException)
        if (credential.enabledAt != null) {
            throw TwoFactorAlreadyEnabledException()
        }

        val secret = secretCrypto.decrypt(credential.secretCiphertext)
        if (!totpCodeService.verify(secret, request.code)) {
            throw InvalidTwoFactorCodeException()
        }

        credential.enabledAt = Instant.now()
        credentialRepository.save(credential)

        return TwoFactorSetupConfirmResponse(
            enabled = true,
            backupCodes = backupCodeService.replaceCodes(credential),
        )
    }

    @Transactional
    fun regenerateBackupCodes(userId: Long): RegenerateBackupCodesResponse {
        val credential = credentialRepository.findByUserIdAndEnabledAtIsNotNull(userId)
            .orElseThrow(::TwoFactorNotEnabledException)
        return RegenerateBackupCodesResponse(backupCodes = backupCodeService.replaceCodes(credential))
    }

    @Transactional
    fun disable(userId: Long): DisableTwoFactorResponse {
        credentialRepository.findByUserId(userId).ifPresent(credentialRepository::delete)
        return DisableTwoFactorResponse()
    }

    private fun buildOtpauthUri(issuer: String, accountName: String, secret: String): String {
        val encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8)
        val encodedAccountName = URLEncoder.encode(accountName, StandardCharsets.UTF_8)
        return "otpauth://totp/$encodedIssuer:$encodedAccountName?secret=$secret&issuer=$encodedIssuer&algorithm=SHA1&digits=6&period=30"
    }
}
