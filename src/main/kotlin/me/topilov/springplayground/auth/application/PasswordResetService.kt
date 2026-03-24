package me.topilov.springplayground.auth.application

import jakarta.servlet.http.HttpServletRequest
import me.topilov.springplayground.auth.dto.ForgotPasswordRequest
import me.topilov.springplayground.auth.dto.ForgotPasswordResponse
import me.topilov.springplayground.auth.dto.ResetPasswordRequest
import me.topilov.springplayground.auth.dto.ResetPasswordResponse
import me.topilov.springplayground.auth.exception.InvalidPasswordResetTokenException
import me.topilov.springplayground.auth.repository.AuthUserRepository
import me.topilov.springplayground.auth.reset.PasswordResetTokenStore
import me.topilov.springplayground.mail.EmailService
import me.topilov.springplayground.mail.MailProperties
import me.topilov.springplayground.mail.PublicUrlBuilder
import me.topilov.springplayground.protection.application.ProtectionService
import me.topilov.springplayground.protection.domain.ProtectionFlow
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PasswordResetService(
    private val authUserRepository: AuthUserRepository,
    private val passwordResetTokenStore: PasswordResetTokenStore,
    private val passwordEncoder: PasswordEncoder,
    private val emailService: EmailService,
    private val mailProperties: MailProperties,
    private val publicUrlBuilder: PublicUrlBuilder,
    private val protectionService: ProtectionService,
) {
    fun forgotPassword(
        request: ForgotPasswordRequest,
        servletRequest: HttpServletRequest,
    ): ForgotPasswordResponse {
        val normalizedEmail = request.email.trim().lowercase()
        protectionService.protect(
            ProtectionFlow.FORGOT_PASSWORD,
            protectionService.buildContext(request.captchaToken, servletRequest, normalizedEmail),
        )
        protectionService.enforceCooldown(ProtectionFlow.FORGOT_PASSWORD, normalizedEmail)

        val user = authUserRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null)
            ?: return ForgotPasswordResponse()
        val userId = requireNotNull(user.id) { "Persisted user id is missing" }

        try {
            val rawToken = passwordResetTokenStore.createToken()
            passwordResetTokenStore.activateToken(userId, rawToken)

            emailService.sendResetPasswordEmail(
                recipientEmail = user.email,
                username = user.username,
                resetUrl = publicUrlBuilder.build(mailProperties.resetPasswordPath, rawToken),
                expiresInMinutes = mailProperties.passwordResetTtl.toMinutes(),
            )
        } catch (exception: RuntimeException) {
            log.warn("Failed to send password reset email to {}", user.email, exception)
        }

        return ForgotPasswordResponse()
    }

    @Transactional
    fun resetPassword(
        request: ResetPasswordRequest,
        servletRequest: HttpServletRequest,
    ): ResetPasswordResponse {
        protectionService.protect(
            ProtectionFlow.RESET_PASSWORD,
            protectionService.buildContext(request.captchaToken, servletRequest, request.token.trim()),
        )
        val userId = passwordResetTokenStore.findUserId(request.token)
            ?: throw InvalidPasswordResetTokenException()

        val user = authUserRepository.findById(userId)
            .orElseThrow(::InvalidPasswordResetTokenException)
        user.passwordHash = requireNotNull(passwordEncoder.encode(request.newPassword)) {
            "Encoded password is missing"
        }
        passwordResetTokenStore.invalidateAllTokensForUser(userId)

        return ResetPasswordResponse()
    }

    companion object {
        private val log = LoggerFactory.getLogger(PasswordResetService::class.java)
    }
}
