package me.topilov.springplayground.auth.passkey.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import me.topilov.springplayground.auth.domain.AuthUser
import me.topilov.springplayground.auth.passkey.ceremony.PasskeyCeremonyStore
import me.topilov.springplayground.auth.passkey.ceremony.RegistrationPasskeyCeremony
import me.topilov.springplayground.auth.passkey.domain.PasskeyCredential
import me.topilov.springplayground.auth.passkey.dto.PasskeyRegistrationOptionsRequest
import me.topilov.springplayground.auth.passkey.dto.PasskeyRegistrationOptionsResponse
import me.topilov.springplayground.auth.passkey.dto.PasskeyRegistrationVerifyRequest
import me.topilov.springplayground.auth.passkey.dto.PasskeySummaryResponse
import me.topilov.springplayground.auth.passkey.exception.DuplicatePasskeyCredentialException
import me.topilov.springplayground.auth.passkey.exception.InvalidPasskeyCeremonyException
import me.topilov.springplayground.auth.passkey.repository.PasskeyCredentialRepository
import me.topilov.springplayground.auth.passkey.webauthn.PasskeyUserIdentity
import me.topilov.springplayground.auth.passkey.webauthn.PasskeyWebAuthnService
import me.topilov.springplayground.auth.repository.AuthUserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.Base64

@Service
class PasskeyRegistrationService(
    private val authUserRepository: AuthUserRepository,
    private val passkeyCredentialRepository: PasskeyCredentialRepository,
    private val passkeyCeremonyStore: PasskeyCeremonyStore,
    private val passkeyWebAuthnService: PasskeyWebAuthnService,
    private val passkeyManagementService: PasskeyManagementService,
) {
    private val objectMapper = jacksonObjectMapper()

    @Transactional
    fun startRegistration(userId: Long, request: PasskeyRegistrationOptionsRequest): PasskeyRegistrationOptionsResponse {
        val user = authUserRepository.findById(userId).orElseThrow(::InvalidPasskeyCeremonyException)
        ensureUserHandle(user)

        val started = passkeyWebAuthnService.beginRegistration(
            PasskeyUserIdentity(
                username = user.username,
                displayName = user.username,
                userHandle = requireNotNull(user.webauthnUserHandle),
            ),
            passkeyCredentialRepository.findAllByUserIdOrderByCreatedAtDesc(userId).map { it.credentialId },
        )

        val ceremonyId = passkeyCeremonyStore.createCeremonyId()
        passkeyCeremonyStore.saveRegistration(
            RegistrationPasskeyCeremony(
                ceremonyId = ceremonyId,
                userId = userId,
                requestJson = started.requestJson,
                nickname = request.nickname?.trim()?.takeIf(String::isNotBlank),
            ),
        )

        return PasskeyRegistrationOptionsResponse(
            ceremonyId = ceremonyId,
            publicKey = started.publicKeyJson,
        )
    }

    @Transactional
    fun finishRegistration(userId: Long, request: PasskeyRegistrationVerifyRequest): PasskeySummaryResponse {
        val ceremony = passkeyCeremonyStore.findRegistration(request.ceremonyId)
            ?: throw InvalidPasskeyCeremonyException()
        if (ceremony.userId != userId) {
            throw InvalidPasskeyCeremonyException()
        }

        val verified = passkeyWebAuthnService.finishRegistration(
            requestJson = ceremony.requestJson,
            credentialJson = objectMapper.writeValueAsString(request.credential),
        )
        if (passkeyCredentialRepository.existsByCredentialId(verified.credentialId)) {
            throw DuplicatePasskeyCredentialException()
        }

        val user = authUserRepository.findById(userId).orElseThrow(::InvalidPasskeyCeremonyException)
        val nickname = request.nickname?.trim()?.takeIf(String::isNotBlank)
            ?: ceremony.nickname
            ?: defaultNickname(userId)
        val passkey = passkeyCredentialRepository.save(
            PasskeyCredential(
                user = user,
                credentialId = verified.credentialId,
                publicKeyCose = verified.publicKeyCose,
                signatureCount = verified.signatureCount,
                aaguid = verified.aaguid,
                transports = verified.transports.joinToString(",").ifBlank { null },
                nickname = nickname,
                authenticatorAttachment = verified.authenticatorAttachment,
                discoverable = verified.discoverable,
                backupEligible = verified.backupEligible,
                backupState = verified.backupState,
            ),
        )
        passkeyCeremonyStore.invalidateRegistration(request.ceremonyId)
        return passkeyManagementService.toSummary(passkey)
    }

    private fun ensureUserHandle(user: AuthUser) {
        if (user.webauthnUserHandle == null) {
            user.webauthnUserHandle = generateUserHandle()
        }
    }

    private fun defaultNickname(userId: Long): String = "Passkey ${passkeyCredentialRepository.countByUserId(userId) + 1}"

    private fun generateUserHandle(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        private val secureRandom = SecureRandom()
    }
}
