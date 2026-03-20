package me.topilov.springplayground.auth.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import me.topilov.springplayground.auth.dto.ForgotPasswordRequest
import me.topilov.springplayground.auth.dto.ForgotPasswordResponse
import me.topilov.springplayground.auth.dto.LoginRequest
import me.topilov.springplayground.auth.dto.LoginResponse
import me.topilov.springplayground.auth.dto.RegisterRequest
import me.topilov.springplayground.auth.dto.RegisterResponse
import me.topilov.springplayground.auth.dto.ResetPasswordRequest
import me.topilov.springplayground.auth.dto.ResetPasswordResponse
import me.topilov.springplayground.auth.service.AuthService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestBody as SpringRequestBody

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Session-based authentication endpoints.")
class AuthController(
    private val authService: AuthService,
) {
    @Operation(
        summary = "Login",
        description = "Authenticates by username or email and creates a server-side session backed by the JSESSIONID cookie.",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(required = true),
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successful login. The response body describes the authenticated user and the response sets JSESSIONID.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = LoginResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Invalid credentials or rejected login request.",
                content = [Content()],
            ),
        ],
    )
    @PostMapping("/login")
    fun login(
        @Valid @SpringRequestBody request: LoginRequest,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): LoginResponse = authService.login(request, servletRequest, servletResponse)

    @Operation(
        summary = "Logout",
        description = "Invalidates the current server-side session. The endpoint returns 204 even when no active session exists.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Logout completed."),
        ],
    )
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
