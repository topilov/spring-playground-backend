package me.topilov.springplayground.auth.passkey.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.topilov.springplayground.auth.dto.LoginResponse
import me.topilov.springplayground.auth.passkey.ceremony.AuthenticationPasskeyCeremony
import me.topilov.springplayground.auth.passkey.ceremony.PasskeyCeremonyStore
import me.topilov.springplayground.auth.passkey.dto.PasskeyLoginOptionsRequest
import me.topilov.springplayground.auth.passkey.dto.PasskeyLoginOptionsResponse
import me.topilov.springplayground.auth.passkey.dto.PasskeyLoginVerifyRequest
import me.topilov.springplayground.auth.passkey.exception.InvalidPasskeyCeremonyException
import me.topilov.springplayground.auth.passkey.exception.PasskeyAuthenticationFailedException
import me.topilov.springplayground.auth.passkey.repository.PasskeyCredentialRepository
import me.topilov.springplayground.auth.passkey.webauthn.PasskeyWebAuthnService
import me.topilov.springplayground.auth.service.SessionLoginService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class PasskeyAuthenticationService(
    private val passkeyCeremonyStore: PasskeyCeremonyStore,
    private val passkeyCredentialRepository: PasskeyCredentialRepository,
    private val passkeyWebAuthnService: PasskeyWebAuthnService,
    private val sessionLoginService: SessionLoginService,
) {
    private val objectMapper = jacksonObjectMapper()

    fun startAuthentication(@Suppress("UNUSED_PARAMETER") request: PasskeyLoginOptionsRequest): PasskeyLoginOptionsResponse {
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
            publicKey = started.publicKeyJson,
        )
    }

    @Transactional
    fun finishAuthentication(
        request: PasskeyLoginVerifyRequest,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): LoginResponse {
        val ceremony = passkeyCeremonyStore.findAuthentication(request.ceremonyId)
            ?: throw InvalidPasskeyCeremonyException()
        val verified = passkeyWebAuthnService.finishAuthentication(
            requestJson = ceremony.requestJson,
            credentialJson = objectMapper.writeValueAsString(request.credential),
        )
        val passkey = passkeyCredentialRepository.findByCredentialId(verified.credentialId)
            .orElseThrow(::PasskeyAuthenticationFailedException)
        passkey.signatureCount = verified.signatureCount
        passkey.backupEligible = verified.backupEligible ?: passkey.backupEligible
        passkey.backupState = verified.backupState ?: passkey.backupState
        passkey.lastUsedAt = Instant.now()
        passkeyCeremonyStore.invalidateAuthentication(request.ceremonyId)
        return sessionLoginService.login(
            authentication = sessionLoginService.createAuthentication(passkey.user),
            servletRequest = servletRequest,
            servletResponse = servletResponse,
        )
    }
}
