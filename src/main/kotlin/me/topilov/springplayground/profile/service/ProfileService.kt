package me.topilov.springplayground.profile.service

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import me.topilov.springplayground.profile.repository.UserProfileRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated
import org.springframework.web.server.ResponseStatusException

@Service
@Validated
class ProfileService(
    private val userProfileRepository: UserProfileRepository,
) {
    @Transactional(readOnly = true)
    fun getCurrentProfile(userId: Long): ProfileResponse = userProfileRepository.findByUserId(userId)
        .map { it.toResponse() }
        .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Profile was not found") }

    @Transactional
    fun updateCurrentProfile(userId: Long, @Valid request: UpdateProfileRequest): ProfileResponse {
        val profile = userProfileRepository.findByUserId(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Profile was not found") }

        profile.displayName = request.displayName.trim()
        profile.bio = request.bio.trim()

        return userProfileRepository.save(profile).toResponse()
    }

    private fun me.topilov.springplayground.profile.domain.UserProfile.toResponse(): ProfileResponse {
        val persistedUser = user ?: throw IllegalStateException("Profile must be linked to a user")
        return ProfileResponse(
            id = id ?: error("Persisted profile must have an id"),
            userId = persistedUser.id ?: error("Persisted user must have an id"),
            username = persistedUser.username,
            email = persistedUser.email,
            role = persistedUser.role.name,
            displayName = displayName,
            bio = bio,
        )
    }

    data class UpdateProfileRequest(
        @field:NotBlank
        @field:Size(max = 120)
        val displayName: String,
        @field:Size(max = 600)
        val bio: String = "",
    )

    data class ProfileResponse(
        val id: Long,
        val userId: Long,
        val username: String,
        val email: String,
        val role: String,
        val displayName: String,
        val bio: String,
    )
}
