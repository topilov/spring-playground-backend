package me.topilov.springplayground.auth.application

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.topilov.springplayground.auth.domain.AuthUser
import me.topilov.springplayground.auth.dto.LoginResponse
import me.topilov.springplayground.auth.dto.TwoFactorBackupCodeLoginVerifyRequest
import me.topilov.springplayground.auth.dto.TwoFactorLoginVerifyRequest
import me.topilov.springplayground.auth.exception.TwoFactorAuthenticationFailedException
import me.topilov.springplayground.auth.service.SessionLoginService
import me.topilov.springplayground.auth.service.TwoFactorLoginService
import me.topilov.springplayground.protection.application.ProtectionService
import me.topilov.springplayground.protection.domain.ProtectionFlow
import org.springframework.stereotype.Service

@Service
class TwoFactorLoginApplicationService(
    private val twoFactorLoginService: TwoFactorLoginService,
    private val protectionService: ProtectionService,
    private val sessionLoginService: SessionLoginService,
) {
    fun completeTotpLogin(
        request: TwoFactorLoginVerifyRequest,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): LoginResponse = completeLogin(
        loginChallengeId = request.loginChallengeId,
        captchaToken = request.captchaToken,
        servletRequest = servletRequest,
        servletResponse = servletResponse,
    ) {
        twoFactorLoginService.completeTotpChallenge(request.loginChallengeId, request.code)
    }

    fun completeBackupCodeLogin(
        request: TwoFactorBackupCodeLoginVerifyRequest,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): LoginResponse = completeLogin(
        loginChallengeId = request.loginChallengeId,
        captchaToken = request.captchaToken,
        servletRequest = servletRequest,
        servletResponse = servletResponse,
    ) {
        twoFactorLoginService.completeBackupCodeChallenge(request.loginChallengeId, request.backupCode)
    }

    private fun completeLogin(
        loginChallengeId: String,
        captchaToken: String?,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
        completeChallenge: () -> AuthUser,
    ): LoginResponse {
        val identifier = twoFactorLoginService.previewChallenge(loginChallengeId).userId.toString()
        protectionService.checkFailureThrottle(ProtectionFlow.TWO_FACTOR_LOGIN, servletRequest, identifier)
        protectionService.protect(
            ProtectionFlow.TWO_FACTOR_LOGIN,
            protectionService.buildContext(captchaToken, servletRequest, identifier),
        )

        val user = try {
            completeChallenge()
        } catch (exception: TwoFactorAuthenticationFailedException) {
            protectionService.recordFailure(ProtectionFlow.TWO_FACTOR_LOGIN, servletRequest, identifier)
            throw exception
        }

        protectionService.clearFailures(ProtectionFlow.TWO_FACTOR_LOGIN, servletRequest, identifier)
        return sessionLoginService.login(
            authentication = sessionLoginService.createAuthentication(user),
            servletRequest = servletRequest,
            servletResponse = servletResponse,
        )
    }
}
