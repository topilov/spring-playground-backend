package me.topilov.springplayground.profile.application

import me.topilov.springplayground.auth.exception.AuthUsernameAlreadyUsedException
import me.topilov.springplayground.auth.repository.AuthUserRepository
import me.topilov.springplayground.profile.dto.ChangePasswordRequest
import me.topilov.springplayground.profile.dto.ChangePasswordResponse
import me.topilov.springplayground.profile.dto.ProfileResponse
import me.topilov.springplayground.profile.dto.UpdateProfileRequest
import me.topilov.springplayground.profile.dto.UpdateUsernameRequest
import me.topilov.springplayground.profile.exception.InvalidCurrentPasswordException
import me.topilov.springplayground.profile.exception.ProfileNotFoundException
import me.topilov.springplayground.profile.mapper.toResponse
import me.topilov.springplayground.profile.repository.UserProfileRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProfileAccountService(
    private val userProfileRepository: UserProfileRepository,
    private val authUserRepository: AuthUserRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    @Transactional
    fun updateCurrentProfile(userId: Long, request: UpdateProfileRequest): ProfileResponse {
        val profile = userProfileRepository.findByUserId(userId)
            .orElseThrow(::ProfileNotFoundException)

        profile.displayName = request.displayName.trim()
        profile.bio = request.bio.trim()

        return userProfileRepository.save(profile).toResponse()
    }

    @Transactional
    fun updateUsername(userId: Long, request: UpdateUsernameRequest): ProfileResponse {
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
    fun changePassword(userId: Long, request: ChangePasswordRequest): ChangePasswordResponse {
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
}
