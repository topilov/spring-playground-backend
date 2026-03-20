package me.topilov.springplayground.profile.service

import jakarta.validation.Valid
import me.topilov.springplayground.profile.dto.ProfileResponse
import me.topilov.springplayground.profile.dto.UpdateProfileRequest
import me.topilov.springplayground.profile.exception.ProfileNotFoundException
import me.topilov.springplayground.profile.mapper.toResponse
import me.topilov.springplayground.profile.repository.UserProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated

@Service
@Validated
class ProfileService(
    private val userProfileRepository: UserProfileRepository,
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
}
