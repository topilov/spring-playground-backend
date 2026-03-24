package me.topilov.springplayground.auth.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.headers.Header
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
import me.topilov.springplayground.auth.dto.ResendVerificationEmailRequest
import me.topilov.springplayground.auth.dto.ResendVerificationEmailResponse
import me.topilov.springplayground.auth.dto.ResetPasswordRequest
import me.topilov.springplayground.auth.dto.ResetPasswordResponse
import me.topilov.springplayground.auth.dto.VerifyEmailRequest
import me.topilov.springplayground.auth.dto.VerifyEmailResponse
import me.topilov.springplayground.auth.dto.TwoFactorLoginChallengeResponse
import me.topilov.springplayground.auth.service.AuthService
import me.topilov.springplayground.auth.service.SessionEstablishedLoginResult
import me.topilov.springplayground.auth.service.TwoFactorRequiredLoginResult
import me.topilov.springplayground.shared.dto.ApiErrorResponse
import me.topilov.springplayground.shared.dto.SimpleErrorResponse
import org.springframework.http.HttpStatus
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
        summary = "Register",
        description = "Creates a new user account and default profile, then sends an email verification link when mail delivery succeeds.",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(required = true),
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "400",
                description = "Validation failed for the submitted registration payload.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiErrorResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "200",
                description = "Successful registration.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = RegisterResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "Username or email is already in use.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = SimpleErrorResponse::class),
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/register")
    fun register(
        @Valid @SpringRequestBody request: RegisterRequest,
    ): RegisterResponse = authService.register(request)

    @Operation(
        summary = "Verify email",
        description = "Confirms a user email using a token that was previously sent by email.",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(required = true),
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Email verification completed.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = VerifyEmailResponse::class),
                    ),
                ],
            ),
            ApiResponse(responseCode = "400", description = "Verification token is invalid or expired."),
        ],
    )
    @PostMapping("/verify-email")
    fun verifyEmail(
        @Valid @SpringRequestBody request: VerifyEmailRequest,
    ): VerifyEmailResponse = authService.verifyEmail(request)

    @Operation(
        summary = "Resend verification email",
        description = "Accepts an email address and sends a fresh verification link when the account exists and is not yet verified. The response is still accepted when the account is missing or already verified.",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(required = true),
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Request accepted.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ResendVerificationEmailResponse::class),
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/resend-verification-email")
    fun resendVerificationEmail(
        @Valid @SpringRequestBody request: ResendVerificationEmailRequest,
    ): ResendVerificationEmailResponse = authService.resendVerificationEmail(request)

    @Operation(
        summary = "Forgot password",
        description = "Accepts an email address and sends a reset link when the account exists. The response is still accepted when the account is missing.",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(required = true),
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "400",
                description = "Validation failed for the submitted email payload.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiErrorResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "200",
                description = "Request accepted.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ForgotPasswordResponse::class),
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/forgot-password")
    fun forgotPassword(
        @Valid @SpringRequestBody request: ForgotPasswordRequest,
    ): ForgotPasswordResponse = authService.forgotPassword(request)

    @Operation(
        summary = "Reset password",
        description = "Resets a password using a valid reset token.",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(required = true),
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Password reset completed.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ResetPasswordResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Validation failed or the reset token is invalid or expired.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(oneOf = [ApiErrorResponse::class, SimpleErrorResponse::class]),
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/reset-password")
    fun resetPassword(
        @Valid @SpringRequestBody request: ResetPasswordRequest,
    ): ResetPasswordResponse = authService.resetPassword(request)

    @Operation(
        summary = "Login",
        description = "Authenticates by username or email and creates a server-side session backed by the JSESSIONID cookie. Browser-based cross-origin clients must send the request with credentials enabled.",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(required = true),
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "400",
                description = "Validation failed for the submitted login payload.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiErrorResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "200",
                description = "Successful login. The response body describes the authenticated user and the response sets JSESSIONID.",
                headers = [
                    Header(
                        name = "Access-Control-Allow-Origin",
                        description = "Returned for allowed browser origins on cross-origin requests.",
                        schema = Schema(type = "string"),
                    ),
                    Header(
                        name = "Access-Control-Allow-Credentials",
                        description = "Returned as true for allowed browser origins so the session cookie can be stored by the client.",
                        schema = Schema(type = "string"),
                    ),
                ],
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = LoginResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "202",
                description = "Password verification succeeded but the account requires a second factor before the authenticated session is created.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = TwoFactorLoginChallengeResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Invalid credentials, rejected login request, or email is not yet verified.",
                content = [Content()],
            ),
        ],
    )
    @PostMapping("/login")
    fun login(
        @Valid @SpringRequestBody request: LoginRequest,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): ResponseEntity<Any> = when (val result = authService.login(request, servletRequest, servletResponse)) {
        is SessionEstablishedLoginResult -> ResponseEntity.ok(result.response)
        is TwoFactorRequiredLoginResult -> ResponseEntity.status(HttpStatus.ACCEPTED).body(result.response)
    }

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
