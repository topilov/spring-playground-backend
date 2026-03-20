package me.topilov.springplayground.auth.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import me.topilov.springplayground.auth.service.AuthService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: AuthService.LoginRequest,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): AuthService.LoginResponse = authService.login(request, servletRequest, servletResponse)

    @PostMapping("/logout")
    fun logout(
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
        authentication: Authentication?,
    ): ResponseEntity<Void> {
        authService.logout(servletRequest, servletResponse, authentication)
        return ResponseEntity.noContent().build()
    }
}
