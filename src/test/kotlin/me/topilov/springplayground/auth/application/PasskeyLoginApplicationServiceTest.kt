package me.topilov.springplayground.auth.application

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.topilov.springplayground.auth.domain.AuthUser
import me.topilov.springplayground.auth.dto.LoginResponse
import me.topilov.springplayground.auth.passkey.dto.PasskeyAuthenticationPublicKeyOptionsDto
import me.topilov.springplayground.auth.passkey.dto.PasskeyLoginOptionsRequest
import me.topilov.springplayground.auth.passkey.dto.PasskeyLoginOptionsResponse
import me.topilov.springplayground.auth.passkey.dto.PasskeyLoginVerifyRequest
import me.topilov.springplayground.auth.passkey.service.PasskeyAuthenticationService
import me.topilov.springplayground.auth.service.SessionLoginService
import me.topilov.springplayground.protection.application.ProtectionContext
import me.topilov.springplayground.protection.application.ProtectionService
import me.topilov.springplayground.protection.domain.ProtectionFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.security.core.Authentication

class PasskeyLoginApplicationServiceTest {
    private val passkeyAuthenticationService = mock(PasskeyAuthenticationService::class.java)
    private val protectionService = mock(ProtectionService::class.java)
    private val sessionLoginService = mock(SessionLoginService::class.java)
    private val servletRequest = mock(HttpServletRequest::class.java)
    private val servletResponse = mock(HttpServletResponse::class.java)

    private val service = PasskeyLoginApplicationService(
        passkeyAuthenticationService = passkeyAuthenticationService,
        protectionService = protectionService,
        sessionLoginService = sessionLoginService,
    )

    @Test
    fun `passkey login start and finish flow through protection and shared session establishment`() {
        val startRequest = PasskeyLoginOptionsRequest(
            usernameOrEmail = "demo@example.com",
            captchaToken = "captcha-token",
        )
        val startContext = ProtectionContext(
            captchaToken = "captcha-token",
            remoteIp = "127.0.0.1",
            identifier = "demo@example.com",
        )
        val optionsResponse = PasskeyLoginOptionsResponse(
            ceremonyId = "ceremony-1",
            publicKey = PasskeyAuthenticationPublicKeyOptionsDto(
                challenge = "challenge",
                rpId = "localhost",
                timeout = 60_000,
                allowCredentials = emptyList(),
                userVerification = "preferred",
            ),
        )
        val finishRequest = PasskeyLoginVerifyRequest(
            ceremonyId = "ceremony-1",
            credential = mapOf("credentialId" to "passkey-1"),
            captchaToken = "captcha-token",
        )
        val finishContext = ProtectionContext(
            captchaToken = "captcha-token",
            remoteIp = "127.0.0.1",
            identifier = "ceremony-1",
        )
        val user = AuthUser(
            id = 1L,
            username = "demo",
            email = "demo@example.com",
            passwordHash = "password-hash",
            emailVerified = true,
        )
        val authentication = mock(Authentication::class.java)
        val loginResponse = LoginResponse(
            userId = 1L,
            username = "demo",
            email = "demo@example.com",
            role = "USER",
        )

        `when`(protectionService.buildContext("captcha-token", servletRequest, "demo@example.com")).thenReturn(startContext)
        `when`(passkeyAuthenticationService.startAuthentication()).thenReturn(optionsResponse)
        `when`(protectionService.buildContext("captcha-token", servletRequest, "ceremony-1")).thenReturn(finishContext)
        `when`(passkeyAuthenticationService.finishAuthentication("ceremony-1", finishRequest.credential)).thenReturn(user)
        `when`(sessionLoginService.createAuthentication(user)).thenReturn(authentication)
        `when`(sessionLoginService.login(authentication, servletRequest, servletResponse)).thenReturn(loginResponse)

        val started = service.startAuthentication(startRequest, servletRequest)
        val finished = service.finishAuthentication(finishRequest, servletRequest, servletResponse)

        verify(protectionService).protect(ProtectionFlow.PASSKEY_LOGIN_OPTIONS, startContext)
        verify(protectionService).protect(ProtectionFlow.PASSKEY_LOGIN_VERIFY, finishContext)
        verify(sessionLoginService).login(authentication, servletRequest, servletResponse)
        assertThat(started).isEqualTo(optionsResponse)
        assertThat(finished).isEqualTo(loginResponse)
    }
}
