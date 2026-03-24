package me.topilov.springplayground.auth.application

import jakarta.servlet.http.HttpServletRequest
import me.topilov.springplayground.auth.dto.ResendVerificationEmailRequest
import me.topilov.springplayground.auth.dto.ResendVerificationEmailResponse
import me.topilov.springplayground.auth.dto.VerifyEmailRequest
import me.topilov.springplayground.auth.dto.VerifyEmailResponse
import me.topilov.springplayground.auth.exception.InvalidEmailVerificationTokenException
import me.topilov.springplayground.auth.repository.AuthUserRepository
import me.topilov.springplayground.auth.verification.EmailVerificationTokenStore
import me.topilov.springplayground.mail.EmailService
import me.topilov.springplayground.mail.MailProperties
import me.topilov.springplayground.mail.PublicUrlBuilder
import me.topilov.springplayground.protection.application.ProtectionService
import me.topilov.springplayground.protection.domain.ProtectionFlow
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EmailVerificationService(
    private val authUserRepository: AuthUserRepository,
    private val emailVerificationTokenStore: EmailVerificationTokenStore,
    private val emailService: EmailService,
    private val mailProperties: MailProperties,
    private val publicUrlBuilder: PublicUrlBuilder,
    private val protectionService: ProtectionService,
) {
    @Transactional
    fun verifyEmail(request: VerifyEmailRequest): VerifyEmailResponse {
        val userId = emailVerificationTokenStore.findUserId(request.token)
            ?: throw InvalidEmailVerificationTokenException()

        val user = authUserRepository.findById(userId)
            .orElseThrow(::InvalidEmailVerificationTokenException)
        user.emailVerified = true
        emailVerificationTokenStore.invalidateToken(request.token)

        return VerifyEmailResponse()
    }

    fun resendVerificationEmail(
        request: ResendVerificationEmailRequest,
        servletRequest: HttpServletRequest,
    ): ResendVerificationEmailResponse {
        val normalizedEmail = request.email.trim().lowercase()
        protectionService.protect(
            ProtectionFlow.RESEND_VERIFICATION_EMAIL,
            protectionService.buildContext(request.captchaToken, servletRequest, normalizedEmail),
        )
        protectionService.enforceCooldown(ProtectionFlow.RESEND_VERIFICATION_EMAIL, normalizedEmail)

        val user = authUserRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null)
            ?: return ResendVerificationEmailResponse()
        if (user.emailVerified) {
            return ResendVerificationEmailResponse()
        }

        try {
            sendRegistrationVerificationEmail(
                userId = requireNotNull(user.id) { "Persisted user id is missing" },
                username = user.username,
                email = user.email,
            )
        } catch (exception: RuntimeException) {
            log.warn("Failed to resend verification email to {}", user.email, exception)
        }

        return ResendVerificationEmailResponse()
    }

    fun sendRegistrationVerificationEmail(userId: Long, username: String, email: String) {
        val rawToken = emailVerificationTokenStore.createToken()
        emailService.sendRegistrationVerificationEmail(
            recipientEmail = email,
            username = username,
            verificationUrl = publicUrlBuilder.build(mailProperties.verifyEmailPath, rawToken),
            expiresInMinutes = mailProperties.emailVerificationTtl.toMinutes(),
        )
        emailVerificationTokenStore.activateToken(userId, rawToken)
    }

    companion object {
        private val log = LoggerFactory.getLogger(EmailVerificationService::class.java)
    }
}
