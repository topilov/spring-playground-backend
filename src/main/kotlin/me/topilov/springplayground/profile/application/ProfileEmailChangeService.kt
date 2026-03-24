package me.topilov.springplayground.profile.application

import jakarta.servlet.http.HttpServletRequest
import me.topilov.springplayground.auth.exception.AuthEmailAlreadyUsedException
import me.topilov.springplayground.auth.repository.AuthUserRepository
import me.topilov.springplayground.mail.EmailService
import me.topilov.springplayground.mail.MailProperties
import me.topilov.springplayground.mail.PublicUrlBuilder
import me.topilov.springplayground.profile.dto.ProfileResponse
import me.topilov.springplayground.profile.dto.RequestEmailChangeRequest
import me.topilov.springplayground.profile.dto.RequestEmailChangeResponse
import me.topilov.springplayground.profile.dto.VerifyEmailChangeRequest
import me.topilov.springplayground.profile.emailchange.PendingEmailChangeTokenStore
import me.topilov.springplayground.profile.exception.InvalidPendingEmailChangeTokenException
import me.topilov.springplayground.profile.exception.NewEmailMatchesCurrentEmailException
import me.topilov.springplayground.profile.exception.ProfileNotFoundException
import me.topilov.springplayground.profile.mapper.toResponse
import me.topilov.springplayground.profile.repository.UserProfileRepository
import me.topilov.springplayground.protection.application.ProtectionService
import me.topilov.springplayground.protection.domain.ProtectionFlow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProfileEmailChangeService(
    private val userProfileRepository: UserProfileRepository,
    private val authUserRepository: AuthUserRepository,
    private val pendingEmailChangeTokenStore: PendingEmailChangeTokenStore,
    private val emailService: EmailService,
    private val mailProperties: MailProperties,
    private val publicUrlBuilder: PublicUrlBuilder,
    private val protectionService: ProtectionService,
) {
    @Transactional
    fun requestEmailChange(userId: Long, request: RequestEmailChangeRequest): RequestEmailChangeResponse {
        val profile = userProfileRepository.findByUserId(userId)
            .orElseThrow(::ProfileNotFoundException)
        val user = profile.user ?: throw ProfileNotFoundException()
        val normalizedEmail = request.newEmail.trim().lowercase()

        if (user.email.equals(normalizedEmail, ignoreCase = true)) {
            throw NewEmailMatchesCurrentEmailException()
        }

        if (authUserRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw AuthEmailAlreadyUsedException(normalizedEmail)
        }

        pendingEmailChangeTokenStore.invalidateAllTokensForUser(userId)
        val rawToken = pendingEmailChangeTokenStore.createToken()
        pendingEmailChangeTokenStore.activateToken(userId, normalizedEmail, rawToken)
        emailService.sendEmailChangeVerificationEmail(
            recipientEmail = normalizedEmail,
            username = user.username,
            verificationUrl = publicUrlBuilder.build(mailProperties.verifyEmailChangePath, rawToken),
            expiresInMinutes = mailProperties.emailVerificationTtl.toMinutes(),
        )

        return RequestEmailChangeResponse()
    }

    @Transactional
    fun verifyEmailChange(
        request: VerifyEmailChangeRequest,
        servletRequest: HttpServletRequest,
    ): ProfileResponse {
        protectionService.protect(
            ProtectionFlow.VERIFY_EMAIL_CHANGE,
            protectionService.buildContext(request.captchaToken, servletRequest, request.token.trim()),
        )
        val pendingChange = pendingEmailChangeTokenStore.findPendingChange(request.token)
            ?: throw InvalidPendingEmailChangeTokenException()

        val profile = userProfileRepository.findByUserId(pendingChange.userId)
            .orElseThrow(::ProfileNotFoundException)
        val user = profile.user ?: throw ProfileNotFoundException()

        if (!user.email.equals(pendingChange.newEmail, ignoreCase = true) &&
            authUserRepository.existsByEmailIgnoreCase(pendingChange.newEmail)
        ) {
            throw AuthEmailAlreadyUsedException(pendingChange.newEmail)
        }

        user.email = pendingChange.newEmail
        user.emailVerified = true
        pendingEmailChangeTokenStore.invalidateToken(request.token)
        pendingEmailChangeTokenStore.invalidateAllTokensForUser(pendingChange.userId)

        return userProfileRepository.save(profile).toResponse()
    }
}
