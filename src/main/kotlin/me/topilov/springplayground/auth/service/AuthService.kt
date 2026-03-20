package me.topilov.springplayground.auth.service

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import me.topilov.springplayground.auth.security.AppUserPrincipal
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextHolderStrategy
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class AuthService(
    private val authenticationManager: AuthenticationManager,
    private val securityContextRepository: SecurityContextRepository,
) {
    private val securityContextHolderStrategy: SecurityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy()
    private val logoutHandler = SecurityContextLogoutHandler()

    fun login(
        @Valid request: LoginRequest,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): LoginResponse {
        servletRequest.getSession(false)?.invalidate()

        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(request.usernameOrEmail, request.password),
        )

        val context = securityContextHolderStrategy.createEmptyContext()
        context.authentication = authentication
        securityContextHolderStrategy.context = context
        securityContextRepository.saveContext(context, servletRequest, servletResponse)

        val principal = authentication.principal as AppUserPrincipal
        return LoginResponse(
            userId = principal.id,
            username = principal.usernameValue,
            email = principal.email,
            role = principal.role.name,
        )
    }

    fun logout(
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
        authentication: Authentication?,
    ) {
        logoutHandler.logout(servletRequest, servletResponse, authentication)
        servletResponse.status = HttpStatus.NO_CONTENT.value()
    }

    data class LoginRequest(
        @field:NotBlank
        val usernameOrEmail: String,
        @field:NotBlank
        val password: String,
    )

    data class LoginResponse(
        val authenticated: Boolean = true,
        val userId: Long,
        val username: String,
        val email: String,
        val role: String,
    )
}
