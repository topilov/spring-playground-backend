package me.topilov.springplayground.auth.application

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.topilov.springplayground.auth.domain.AuthUser
import me.topilov.springplayground.auth.dto.LoginResponse
import me.topilov.springplayground.auth.dto.TwoFactorLoginVerifyRequest
import me.topilov.springplayground.auth.exception.TwoFactorAuthenticationFailedException
import me.topilov.springplayground.auth.service.SessionLoginService
import me.topilov.springplayground.auth.service.StoredTwoFactorLoginChallenge
import me.topilov.springplayground.auth.service.TwoFactorLoginService
import me.topilov.springplayground.protection.application.ProtectionContext
import me.topilov.springplayground.protection.application.ProtectionService
import me.topilov.springplayground.protection.domain.ProtectionFlow
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.security.core.Authentication

class TwoFactorLoginApplicationServiceTest {
    private val twoFactorLoginService = mock(TwoFactorLoginService::class.java)
    private val protectionService = mock(ProtectionService::class.java)
    private val sessionLoginService = mock(SessionLoginService::class.java)
    private val servletRequest = mock(HttpServletRequest::class.java)
    private val servletResponse = mock(HttpServletResponse::class.java)

    private val service = TwoFactorLoginApplicationService(
        twoFactorLoginService = twoFactorLoginService,
        protectionService = protectionService,
        sessionLoginService = sessionLoginService,
    )

    @Test
    fun `successful totp completion uses protection and shared session login service`() {
        val request = TwoFactorLoginVerifyRequest(
            loginChallengeId = "challenge-1",
            code = "123456",
            captchaToken = "captcha-token",
        )
        val preview = StoredTwoFactorLoginChallenge(
            loginChallengeId = "challenge-1",
            userId = 42L,
            expiresAt = java.time.Instant.parse("2026-03-25T00:00:00Z"),
        )
        val protectionContext = ProtectionContext(
            captchaToken = "captcha-token",
            remoteIp = "127.0.0.1",
            identifier = "42",
        )
        val user = AuthUser(
            id = 42L,
            username = "demo",
            email = "demo@example.com",
            passwordHash = "password-hash",
            emailVerified = true,
        )
        val authentication = mock(Authentication::class.java)
        val response = LoginResponse(
            userId = 42L,
            username = "demo",
            email = "demo@example.com",
            role = "USER",
        )

        `when`(twoFactorLoginService.previewChallenge("challenge-1")).thenReturn(preview)
        `when`(protectionService.buildContext("captcha-token", servletRequest, "42")).thenReturn(protectionContext)
        `when`(twoFactorLoginService.completeTotpChallenge("challenge-1", "123456")).thenReturn(user)
        `when`(sessionLoginService.createAuthentication(user)).thenReturn(authentication)
        `when`(sessionLoginService.login(authentication, servletRequest, servletResponse)).thenReturn(response)

        val result = service.completeTotpLogin(request, servletRequest, servletResponse)

        verify(protectionService).checkFailureThrottle(ProtectionFlow.TWO_FACTOR_LOGIN, servletRequest, "42")
        verify(protectionService).protect(ProtectionFlow.TWO_FACTOR_LOGIN, protectionContext)
        verify(protectionService).clearFailures(ProtectionFlow.TWO_FACTOR_LOGIN, servletRequest, "42")
        verify(sessionLoginService).login(authentication, servletRequest, servletResponse)
        assertThat(result).isEqualTo(response)
    }

    @Test
    fun `failed totp completion records failure before rethrowing`() {
        val request = TwoFactorLoginVerifyRequest(
            loginChallengeId = "challenge-1",
            code = "123456",
            captchaToken = "captcha-token",
        )
        val preview = StoredTwoFactorLoginChallenge(
            loginChallengeId = "challenge-1",
            userId = 42L,
            expiresAt = java.time.Instant.parse("2026-03-25T00:00:00Z"),
        )
        val protectionContext = ProtectionContext(
            captchaToken = "captcha-token",
            remoteIp = "127.0.0.1",
            identifier = "42",
        )

        `when`(twoFactorLoginService.previewChallenge("challenge-1")).thenReturn(preview)
        `when`(protectionService.buildContext("captcha-token", servletRequest, "42")).thenReturn(protectionContext)
        `when`(twoFactorLoginService.completeTotpChallenge("challenge-1", "123456"))
            .thenThrow(TwoFactorAuthenticationFailedException())

        assertThatThrownBy {
            service.completeTotpLogin(request, servletRequest, servletResponse)
        }.isInstanceOf(TwoFactorAuthenticationFailedException::class.java)

        verify(protectionService).recordFailure(ProtectionFlow.TWO_FACTOR_LOGIN, servletRequest, "42")
    }
}
