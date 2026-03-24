package me.topilov.springplayground.auth.application

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.topilov.springplayground.auth.dto.LoginRequest
import me.topilov.springplayground.auth.exception.EmailNotVerifiedException
import me.topilov.springplayground.auth.service.PasswordLoginResult
import me.topilov.springplayground.auth.service.SessionEstablishedLoginResult
import me.topilov.springplayground.auth.service.SessionLoginService
import me.topilov.springplayground.auth.service.TwoFactorLoginService
import me.topilov.springplayground.auth.service.TwoFactorRequiredLoginResult
import me.topilov.springplayground.protection.application.ProtectionService
import me.topilov.springplayground.protection.domain.ProtectionFlow
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler
import org.springframework.stereotype.Service

@Service
class SessionAuthenticationService(
    private val authenticationManager: AuthenticationManager,
    private val sessionLoginService: SessionLoginService,
    private val twoFactorLoginService: TwoFactorLoginService,
    private val protectionService: ProtectionService,
) {
    private val logoutHandler = SecurityContextLogoutHandler()

    fun login(
        request: LoginRequest,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): PasswordLoginResult {
        val identifier = request.usernameOrEmail.trim().lowercase()
        protectionService.checkFailureThrottle(ProtectionFlow.LOGIN, servletRequest, identifier)
        protectionService.protect(
            ProtectionFlow.LOGIN,
            protectionService.buildContext(request.captchaToken, servletRequest, identifier),
        )

        val authentication = try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.usernameOrEmail, request.password),
            )
        } catch (exception: AuthenticationException) {
            protectionService.recordFailure(ProtectionFlow.LOGIN, servletRequest, identifier)
            throw exception
        }

        val principal = try {
            sessionLoginService.requireLoginAllowed(authentication)
        } catch (exception: EmailNotVerifiedException) {
            protectionService.recordFailure(ProtectionFlow.LOGIN, servletRequest, identifier)
            throw exception
        }
        protectionService.clearFailures(ProtectionFlow.LOGIN, servletRequest, identifier)
        if (twoFactorLoginService.isEnabledForUser(principal.id)) {
            return TwoFactorRequiredLoginResult(twoFactorLoginService.createLoginChallenge(principal.id))
        }

        return SessionEstablishedLoginResult(
            sessionLoginService.login(authentication, servletRequest, servletResponse),
        )
    }

    fun logout(
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
        authentication: Authentication?,
    ) {
        logoutHandler.logout(servletRequest, servletResponse, authentication)
    }
}
