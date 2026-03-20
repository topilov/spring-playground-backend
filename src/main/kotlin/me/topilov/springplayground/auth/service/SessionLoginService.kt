package me.topilov.springplayground.auth.service

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.topilov.springplayground.auth.domain.AuthUser
import me.topilov.springplayground.auth.dto.LoginResponse
import me.topilov.springplayground.auth.exception.EmailNotVerifiedException
import me.topilov.springplayground.auth.security.AppUserPrincipal
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextHolderStrategy
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.stereotype.Service

@Service
class SessionLoginService(
    private val securityContextRepository: SecurityContextRepository,
) {
    private val securityContextHolderStrategy: SecurityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy()

    fun login(
        authentication: Authentication,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): LoginResponse {
        val principal = authentication.principal as? AppUserPrincipal
            ?: throw BadCredentialsException("Unsupported principal")
        if (!principal.isEnabled) {
            throw BadCredentialsException("User is disabled")
        }
        if (!principal.emailVerified) {
            throw EmailNotVerifiedException()
        }

        servletRequest.getSession(false)?.invalidate()

        val context = securityContextHolderStrategy.createEmptyContext()
        context.authentication = authentication
        securityContextHolderStrategy.context = context
        securityContextRepository.saveContext(context, servletRequest, servletResponse)

        return LoginResponse(
            userId = principal.id,
            username = principal.usernameValue,
            email = principal.email,
            role = principal.role.name,
        )
    }

    fun createAuthentication(user: AuthUser): Authentication {
        val principal = AppUserPrincipal(
            id = requireNotNull(user.id) { "Persisted user id is missing" },
            usernameValue = user.username,
            email = user.email,
            passwordHash = user.passwordHash,
            role = user.role,
            enabledValue = user.enabled,
            emailVerified = user.emailVerified,
        )

        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities)
    }
}
