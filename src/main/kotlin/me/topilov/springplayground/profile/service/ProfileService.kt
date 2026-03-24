package me.topilov.springplayground.profile.service

import jakarta.validation.Valid
import me.topilov.springplayground.auth.exception.AuthEmailAlreadyUsedException
import me.topilov.springplayground.auth.exception.AuthUsernameAlreadyUsedException
import me.topilov.springplayground.auth.repository.AuthUserRepository
import me.topilov.springplayground.mail.EmailService
import me.topilov.springplayground.mail.MailProperties
import me.topilov.springplayground.profile.dto.ChangePasswordRequest
import me.topilov.springplayground.profile.dto.ChangePasswordResponse
import me.topilov.springplayground.profile.dto.ProfileResponse
import me.topilov.springplayground.profile.dto.RequestEmailChangeRequest
import me.topilov.springplayground.profile.dto.RequestEmailChangeResponse
import me.topilov.springplayground.profile.dto.UpdateProfileRequest
import me.topilov.springplayground.profile.dto.UpdateUsernameRequest
import me.topilov.springplayground.profile.dto.VerifyEmailChangeRequest
import me.topilov.springplayground.profile.emailchange.PendingEmailChangeTokenStore
import me.topilov.springplayground.profile.exception.InvalidCurrentPasswordException
import me.topilov.springplayground.profile.exception.InvalidPendingEmailChangeTokenException
import me.topilov.springplayground.profile.exception.NewEmailMatchesCurrentEmailException
import me.topilov.springplayground.profile.exception.ProfileNotFoundException
import me.topilov.springplayground.profile.mapper.toResponse
import me.topilov.springplayground.profile.repository.UserProfileRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Service
@Validated
class ProfileService(
    private val userProfileRepository: UserProfileRepository,
    private val authUserRepository: AuthUserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val pendingEmailChangeTokenStore: PendingEmailChangeTokenStore,
    private val emailService: EmailService,
    private val mailProperties: MailProperties,
) {
    @Transactional(readOnly = true)
    fun getCurrentProfile(userId: Long): ProfileResponse = userProfileRepository.findByUserId(userId)
        .map { it.toResponse() }
        .orElseThrow(::ProfileNotFoundException)

    @Transactional
    fun updateCurrentProfile(userId: Long, @Valid request: UpdateProfileRequest): ProfileResponse {
        val profile = userProfileRepository.findByUserId(userId)
            .orElseThrow(::ProfileNotFoundException)

        profile.displayName = request.displayName.trim()
        profile.bio = request.bio.trim()

        return userProfileRepository.save(profile).toResponse()
    }

    @Transactional
    fun updateUsername(userId: Long, @Valid request: UpdateUsernameRequest): ProfileResponse {
        val profile = userProfileRepository.findByUserId(userId)
            .orElseThrow(::ProfileNotFoundException)
        val user = profile.user ?: throw ProfileNotFoundException()
        val username = request.username.trim()

        if (user.username.equals(username, ignoreCase = true)) {
            return profile.toResponse()
        }

        if (authUserRepository.existsByUsernameIgnoreCase(username)) {
            throw AuthUsernameAlreadyUsedException(username)
        }

        user.username = username
        return userProfileRepository.save(profile).toResponse()
    }

    @Transactional
    fun changePassword(userId: Long, @Valid request: ChangePasswordRequest): ChangePasswordResponse {
        val profile = userProfileRepository.findByUserId(userId)
            .orElseThrow(::ProfileNotFoundException)
        val user = profile.user ?: throw ProfileNotFoundException()

        if (!passwordEncoder.matches(request.currentPassword, user.passwordHash)) {
            throw InvalidCurrentPasswordException()
        }

        user.passwordHash = requireNotNull(passwordEncoder.encode(request.newPassword)) {
            "Encoded password is missing"
        }
        return ChangePasswordResponse()
    }

    @Transactional
    fun requestEmailChange(userId: Long, @Valid request: RequestEmailChangeRequest): RequestEmailChangeResponse {
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
        emailService.sendVerificationEmail(
            recipientEmail = normalizedEmail,
            username = user.username,
            verificationUrl = buildEmailChangeVerificationUrl(rawToken),
            expiresInMinutes = mailProperties.emailVerificationTtl.toMinutes(),
        )

        return RequestEmailChangeResponse()
    }

    @Transactional
    fun verifyEmailChange(@Valid request: VerifyEmailChangeRequest): ProfileResponse {
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

    private fun buildEmailChangeVerificationUrl(rawToken: String): String {
        val baseUrl = mailProperties.publicBaseUrl.trimEnd('/')
        val path = if (mailProperties.verifyEmailPath.startsWith("/")) {
            mailProperties.verifyEmailPath
        } else {
            "/${mailProperties.verifyEmailPath}"
        }

        return "$baseUrl$path?token=${URLEncoder.encode(rawToken, StandardCharsets.UTF_8)}"
    }
}
