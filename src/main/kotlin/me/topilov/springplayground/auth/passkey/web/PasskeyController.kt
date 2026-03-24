package me.topilov.springplayground.auth.passkey.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import me.topilov.springplayground.auth.dto.LoginResponse
import me.topilov.springplayground.auth.passkey.dto.PasskeyLoginOptionsRequest
import me.topilov.springplayground.auth.passkey.dto.PasskeyLoginOptionsResponse
import me.topilov.springplayground.auth.passkey.dto.PasskeyLoginVerifyRequest
import me.topilov.springplayground.auth.passkey.dto.PasskeyRegistrationOptionsRequest
import me.topilov.springplayground.auth.passkey.dto.PasskeyRegistrationOptionsResponse
import me.topilov.springplayground.auth.passkey.dto.PasskeyRegistrationVerifyRequest
import me.topilov.springplayground.auth.passkey.dto.PasskeySummaryResponse
import me.topilov.springplayground.auth.passkey.dto.RenamePasskeyRequest
import me.topilov.springplayground.auth.passkey.service.PasskeyAuthenticationService
import me.topilov.springplayground.auth.passkey.service.PasskeyManagementService
import me.topilov.springplayground.auth.passkey.service.PasskeyRegistrationService
import me.topilov.springplayground.auth.security.AppUserPrincipal
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class PasskeyController(
    private val passkeyRegistrationService: PasskeyRegistrationService,
    private val passkeyManagementService: PasskeyManagementService,
    private val passkeyAuthenticationService: PasskeyAuthenticationService,
) {
    @PostMapping("/passkeys/register/options")
    fun startRegistration(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @Valid @RequestBody request: PasskeyRegistrationOptionsRequest,
    ): PasskeyRegistrationOptionsResponse = passkeyRegistrationService.startRegistration(principal.id, request)

    @PostMapping("/passkeys/register/verify")
    fun finishRegistration(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @Valid @RequestBody request: PasskeyRegistrationVerifyRequest,
    ): PasskeySummaryResponse = passkeyRegistrationService.finishRegistration(principal.id, request)

    @GetMapping("/passkeys")
    fun listPasskeys(@AuthenticationPrincipal principal: AppUserPrincipal): List<PasskeySummaryResponse> =
        passkeyManagementService.listForUser(principal.id)

    @PatchMapping("/passkeys/{id}")
    fun renamePasskey(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable id: Long,
        @Valid @RequestBody request: RenamePasskeyRequest,
    ): PasskeySummaryResponse = passkeyManagementService.rename(principal.id, id, request)

    @DeleteMapping("/passkeys/{id}")
    fun deletePasskey(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        passkeyManagementService.delete(principal.id, id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/passkey-login/options")
    fun startPasskeyLogin(
        @Valid @RequestBody request: PasskeyLoginOptionsRequest,
        servletRequest: HttpServletRequest,
    ): PasskeyLoginOptionsResponse = passkeyAuthenticationService.startAuthentication(request, servletRequest)

    @PostMapping("/passkey-login/verify")
    fun finishPasskeyLogin(
        @Valid @RequestBody request: PasskeyLoginVerifyRequest,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): LoginResponse = passkeyAuthenticationService.finishAuthentication(request, servletRequest, servletResponse)
}
