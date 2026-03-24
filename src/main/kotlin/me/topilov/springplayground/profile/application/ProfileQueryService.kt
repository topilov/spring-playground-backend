package me.topilov.springplayground.profile.application

import me.topilov.springplayground.profile.dto.ProfileResponse
import me.topilov.springplayground.profile.exception.ProfileNotFoundException
import me.topilov.springplayground.profile.mapper.toResponse
import me.topilov.springplayground.profile.repository.UserProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProfileQueryService(
    private val userProfileRepository: UserProfileRepository,
) {
    @Transactional(readOnly = true)
    fun getCurrentProfile(userId: Long): ProfileResponse = userProfileRepository.findByUserId(userId)
        .map { it.toResponse() }
        .orElseThrow(::ProfileNotFoundException)
}
