package me.topilov.springplayground.auth.application

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.topilov.springplayground.auth.dto.LoginResponse
import me.topilov.springplayground.auth.passkey.dto.PasskeyLoginOptionsRequest
import me.topilov.springplayground.auth.passkey.dto.PasskeyLoginOptionsResponse
import me.topilov.springplayground.auth.passkey.dto.PasskeyLoginVerifyRequest
import me.topilov.springplayground.auth.passkey.service.PasskeyAuthenticationService
import me.topilov.springplayground.auth.service.SessionLoginService
import me.topilov.springplayground.protection.application.ProtectionService
import me.topilov.springplayground.protection.domain.ProtectionFlow
import org.springframework.stereotype.Service

@Service
class PasskeyLoginApplicationService(
    private val passkeyAuthenticationService: PasskeyAuthenticationService,
    private val protectionService: ProtectionService,
    private val sessionLoginService: SessionLoginService,
) {
    fun startAuthentication(
        request: PasskeyLoginOptionsRequest,
        servletRequest: HttpServletRequest,
    ): PasskeyLoginOptionsResponse {
        protectionService.protect(
            ProtectionFlow.PASSKEY_LOGIN_OPTIONS,
            protectionService.buildContext(
                captchaToken = request.captchaToken,
                request = servletRequest,
                identifier = request.usernameOrEmail?.trim()?.lowercase(),
            ),
        )
        return passkeyAuthenticationService.startAuthentication()
    }

    fun finishAuthentication(
        request: PasskeyLoginVerifyRequest,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): LoginResponse {
        protectionService.protect(
            ProtectionFlow.PASSKEY_LOGIN_VERIFY,
            protectionService.buildContext(request.captchaToken, servletRequest, request.ceremonyId),
        )
        val user = passkeyAuthenticationService.finishAuthentication(request.ceremonyId, request.credential)
        return sessionLoginService.login(
            authentication = sessionLoginService.createAuthentication(user),
            servletRequest = servletRequest,
            servletResponse = servletResponse,
        )
    }
}
