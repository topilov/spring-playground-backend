package me.topilov.springplayground.auth.service

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import me.topilov.springplayground.abuse.AbuseProtectionFlow
import me.topilov.springplayground.abuse.AbuseProtectionService
import me.topilov.springplayground.auth.domain.AuthUser
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
import me.topilov.springplayground.auth.exception.AuthEmailAlreadyUsedException
import me.topilov.springplayground.auth.exception.AuthUsernameAlreadyUsedException
import me.topilov.springplayground.auth.exception.EmailNotVerifiedException
import me.topilov.springplayground.auth.exception.InvalidPasswordResetTokenException
import me.topilov.springplayground.auth.exception.InvalidEmailVerificationTokenException
import me.topilov.springplayground.auth.reset.PasswordResetTokenStore
import me.topilov.springplayground.auth.repository.AuthUserRepository
import me.topilov.springplayground.auth.security.AppUserPrincipal
import me.topilov.springplayground.auth.verification.EmailVerificationTokenStore
import me.topilov.springplayground.mail.EmailService
import me.topilov.springplayground.mail.MailProperties
import me.topilov.springplayground.profile.domain.UserProfile
import me.topilov.springplayground.profile.repository.UserProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.validation.annotation.Validated
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

@Service
@Validated
class AuthService(
    private val authenticationManager: AuthenticationManager,
    private val sessionLoginService: SessionLoginService,
    private val twoFactorLoginService: TwoFactorLoginService,
    private val authUserRepository: AuthUserRepository,
    private val userProfileRepository: UserProfileRepository,
    private val passwordResetTokenStore: PasswordResetTokenStore,
    private val passwordEncoder: PasswordEncoder,
    private val emailVerificationTokenStore: EmailVerificationTokenStore,
    private val emailService: EmailService,
    private val mailProperties: MailProperties,
    private val abuseProtectionService: AbuseProtectionService,
    transactionManager: PlatformTransactionManager,
) {
    private val logoutHandler = SecurityContextLogoutHandler()
    private val transactionTemplate = TransactionTemplate(transactionManager)

    fun register(
        @Valid request: RegisterRequest,
        servletRequest: HttpServletRequest,
    ): RegisterResponse {
        abuseProtectionService.protect(
            AbuseProtectionFlow.REGISTER,
            abuseProtectionService.buildContext(request.captchaToken, servletRequest),
        )
        val username = request.username.trim()
        val email = request.email.trim().lowercase()

        val registration = try {
            requireNotNull(transactionTemplate.execute<RegisteredUser> {
                if (authUserRepository.existsByUsernameIgnoreCase(username)) {
                    throw AuthUsernameAlreadyUsedException(username)
                }

                if (authUserRepository.existsByEmailIgnoreCase(email)) {
                    throw AuthEmailAlreadyUsedException(email)
                }

                val user = authUserRepository.save(
                    AuthUser(
                        username = username,
                        email = email,
                        passwordHash = requireNotNull(passwordEncoder.encode(request.password)) {
                            "Encoded password is missing"
                        },
                        emailVerified = false,
                    ),
                )

                userProfileRepository.save(
                    UserProfile(
                        user = user,
                        displayName = username,
                        bio = "",
                    ),
                )

                RegisteredUser(
                    userId = requireNotNull(user.id) { "Persisted user id is missing" },
                    username = user.username,
                    email = user.email,
                )
            })
        } catch (exception: DataIntegrityViolationException) {
            when {
                authUserRepository.existsByUsernameIgnoreCase(username) -> throw AuthUsernameAlreadyUsedException(username)
                authUserRepository.existsByEmailIgnoreCase(email) -> throw AuthEmailAlreadyUsedException(email)
                else -> throw exception
            }
        }

        try {
            sendRegistrationVerificationEmail(registration)
        } catch (exception: RuntimeException) {
            log.warn("Failed to send verification email to {}", registration.email, exception)
        }

        return RegisterResponse(
            userId = registration.userId,
            username = registration.username,
            email = registration.email,
        )
    }

    fun verifyEmail(@Valid request: VerifyEmailRequest): VerifyEmailResponse {
        val userId = emailVerificationTokenStore.findUserId(request.token)
            ?: throw InvalidEmailVerificationTokenException()

        transactionTemplate.executeWithoutResult {
            val user = authUserRepository.findById(userId)
                .orElseThrow(::InvalidEmailVerificationTokenException)
            user.emailVerified = true
        }
        emailVerificationTokenStore.invalidateToken(request.token)

        return VerifyEmailResponse()
    }

    fun resendVerificationEmail(
        @Valid request: ResendVerificationEmailRequest,
        servletRequest: HttpServletRequest,
    ): ResendVerificationEmailResponse {
        val normalizedEmail = request.email.trim().lowercase()
        abuseProtectionService.protect(
            AbuseProtectionFlow.RESEND_VERIFICATION_EMAIL,
            abuseProtectionService.buildContext(request.captchaToken, servletRequest, normalizedEmail),
        )
        abuseProtectionService.enforceCooldown(AbuseProtectionFlow.RESEND_VERIFICATION_EMAIL, normalizedEmail)

        val dispatch = transactionTemplate.execute<RegisteredUser?> {
            val user = authUserRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null)
                ?: return@execute null
            if (user.emailVerified) {
                return@execute null
            }

            RegisteredUser(
                userId = requireNotNull(user.id) { "Persisted user id is missing" },
                username = user.username,
                email = user.email,
            )
        }

        if (dispatch != null) {
            try {
                sendRegistrationVerificationEmail(dispatch)
            } catch (exception: RuntimeException) {
                log.warn("Failed to resend verification email to {}", dispatch.email, exception)
            }
        }

        return ResendVerificationEmailResponse()
    }

    fun forgotPassword(
        @Valid request: ForgotPasswordRequest,
        servletRequest: HttpServletRequest,
    ): ForgotPasswordResponse {
        val normalizedEmail = request.email.trim().lowercase()
        abuseProtectionService.protect(
            AbuseProtectionFlow.FORGOT_PASSWORD,
            abuseProtectionService.buildContext(request.captchaToken, servletRequest, normalizedEmail),
        )
        abuseProtectionService.enforceCooldown(AbuseProtectionFlow.FORGOT_PASSWORD, normalizedEmail)

        val dispatch = transactionTemplate.execute<PasswordResetDispatch?> {
            val user = authUserRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null)
                ?: return@execute null
            val userId = requireNotNull(user.id) { "Persisted user id is missing" }

            PasswordResetDispatch(
                userId = userId,
                recipientEmail = user.email,
                username = user.username,
            )
        } ?: return ForgotPasswordResponse()

        try {
            val rawToken = passwordResetTokenStore.createToken()
            passwordResetTokenStore.activateToken(dispatch.userId, rawToken)

            emailService.sendResetPasswordEmail(
                recipientEmail = dispatch.recipientEmail,
                username = dispatch.username,
                resetUrl = buildResetUrl(rawToken),
                expiresInMinutes = mailProperties.passwordResetTtl.toMinutes(),
            )
        } catch (exception: RuntimeException) {
            log.warn("Failed to send password reset email to {}", dispatch.recipientEmail, exception)
        }

        return ForgotPasswordResponse()
    }

    fun resetPassword(
        @Valid request: ResetPasswordRequest,
        servletRequest: HttpServletRequest,
    ): ResetPasswordResponse {
        abuseProtectionService.protect(
            AbuseProtectionFlow.RESET_PASSWORD,
            abuseProtectionService.buildContext(request.captchaToken, servletRequest, request.token.trim()),
        )
        val userId = passwordResetTokenStore.findUserId(request.token)
            ?: throw InvalidPasswordResetTokenException()

        transactionTemplate.executeWithoutResult {
            val user = authUserRepository.findById(userId)
                .orElseThrow(::InvalidPasswordResetTokenException)
            user.passwordHash = requireNotNull(passwordEncoder.encode(request.newPassword)) {
                "Encoded password is missing"
            }
        }
        passwordResetTokenStore.invalidateAllTokensForUser(userId)

        return ResetPasswordResponse()
    }

    fun login(
        @Valid request: LoginRequest,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): PasswordLoginResult {
        val identifier = request.usernameOrEmail.trim().lowercase()
        abuseProtectionService.checkFailureThrottle(AbuseProtectionFlow.LOGIN, servletRequest, identifier)
        abuseProtectionService.protect(
            AbuseProtectionFlow.LOGIN,
            abuseProtectionService.buildContext(request.captchaToken, servletRequest, identifier),
        )

        val authentication = try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.usernameOrEmail, request.password),
            )
        } catch (exception: AuthenticationException) {
            abuseProtectionService.recordFailure(AbuseProtectionFlow.LOGIN, servletRequest, identifier)
            throw exception
        }

        val principal = try {
            sessionLoginService.requireLoginAllowed(authentication)
        } catch (exception: EmailNotVerifiedException) {
            abuseProtectionService.recordFailure(AbuseProtectionFlow.LOGIN, servletRequest, identifier)
            throw exception
        }
        abuseProtectionService.clearFailures(AbuseProtectionFlow.LOGIN, servletRequest, identifier)
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

    private fun buildResetUrl(rawToken: String): String {
        return buildPublicUrl(mailProperties.resetPasswordPath, rawToken)
    }

    private fun buildVerificationUrl(rawToken: String): String {
        return buildPublicUrl(mailProperties.verifyEmailPath, rawToken)
    }

    private fun buildPublicUrl(pathValue: String, rawToken: String): String {
        val baseUrl = mailProperties.publicBaseUrl.trimEnd('/')
        val path = if (pathValue.startsWith("/")) {
            pathValue
        } else {
            "/$pathValue"
        }

        return "$baseUrl$path?token=${URLEncoder.encode(rawToken, StandardCharsets.UTF_8)}"
    }

    private fun sendRegistrationVerificationEmail(registration: RegisteredUser) {
        val rawToken = emailVerificationTokenStore.createToken()
        emailService.sendRegistrationVerificationEmail(
            recipientEmail = registration.email,
            username = registration.username,
            verificationUrl = buildVerificationUrl(rawToken),
            expiresInMinutes = mailProperties.emailVerificationTtl.toMinutes(),
        )
        emailVerificationTokenStore.activateToken(registration.userId, rawToken)
    }

    private data class RegisteredUser(
        val userId: Long,
        val username: String,
        val email: String,
    )

    private data class PasswordResetDispatch(
        val userId: Long,
        val recipientEmail: String,
        val username: String,
    )

    companion object {
        private val log = LoggerFactory.getLogger(AuthService::class.java)
    }
}
