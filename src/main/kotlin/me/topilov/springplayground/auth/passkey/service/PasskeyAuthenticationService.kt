package me.topilov.springplayground.auth.passkey.service

import com.fasterxml.jackson.databind.ObjectMapper
import me.topilov.springplayground.auth.domain.AuthUser
import me.topilov.springplayground.auth.passkey.PasskeyOptionsMapper
import me.topilov.springplayground.auth.passkey.ceremony.AuthenticationPasskeyCeremony
import me.topilov.springplayground.auth.passkey.ceremony.PasskeyCeremonyStore
import me.topilov.springplayground.auth.passkey.dto.PasskeyLoginOptionsResponse
import me.topilov.springplayground.auth.passkey.exception.InvalidPasskeyCeremonyException
import me.topilov.springplayground.auth.passkey.exception.PasskeyAuthenticationFailedException
import me.topilov.springplayground.auth.passkey.repository.PasskeyCredentialRepository
import me.topilov.springplayground.auth.passkey.webauthn.PasskeyWebAuthnService
import me.topilov.springplayground.auth.repository.AuthUserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class PasskeyAuthenticationService(
    private val passkeyCeremonyStore: PasskeyCeremonyStore,
    private val passkeyCredentialRepository: PasskeyCredentialRepository,
    private val authUserRepository: AuthUserRepository,
    private val passkeyWebAuthnService: PasskeyWebAuthnService,
    private val passkeyOptionsMapper: PasskeyOptionsMapper,
    private val objectMapper: ObjectMapper,
) {
    fun startAuthentication(): PasskeyLoginOptionsResponse {
        val started = passkeyWebAuthnService.beginAuthentication()
        val ceremonyId = passkeyCeremonyStore.createCeremonyId()
        passkeyCeremonyStore.saveAuthentication(
            AuthenticationPasskeyCeremony(
                ceremonyId = ceremonyId,
                requestJson = started.requestJson,
            ),
        )

        return PasskeyLoginOptionsResponse(
            ceremonyId = ceremonyId,
            publicKey = passkeyOptionsMapper.authenticationOptionsFromCredentialsGetJson(started.credentialRequestJson),
        )
    }

    @Transactional
    fun finishAuthentication(
        ceremonyId: String,
        credential: Map<String, Any?>,
    ): AuthUser {
        val ceremony = passkeyCeremonyStore.findAuthentication(ceremonyId)
            ?: throw InvalidPasskeyCeremonyException()
        val verified = passkeyWebAuthnService.finishAuthentication(
            requestJson = ceremony.requestJson,
            credentialJson = objectMapper.writeValueAsString(credential),
        )
        val passkey = passkeyCredentialRepository.findByCredentialId(verified.credentialId)
            .orElseThrow(::PasskeyAuthenticationFailedException)
        passkey.signatureCount = verified.signatureCount
        passkey.backupEligible = verified.backupEligible ?: passkey.backupEligible
        passkey.backupState = verified.backupState ?: passkey.backupState
        passkey.lastUsedAt = Instant.now()
        passkeyCeremonyStore.invalidateAuthentication(ceremonyId)
        return authUserRepository.findById(requireNotNull(passkey.user.id) { "Persisted user id is missing" })
            .orElseThrow(::PasskeyAuthenticationFailedException)
    }
}
