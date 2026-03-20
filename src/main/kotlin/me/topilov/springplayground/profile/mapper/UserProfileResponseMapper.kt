package me.topilov.springplayground.profile.mapper

import me.topilov.springplayground.profile.domain.UserProfile
import me.topilov.springplayground.profile.dto.ProfileResponse
import me.topilov.springplayground.profile.exception.PersistedProfileIdMissingException
import me.topilov.springplayground.profile.exception.PersistedProfileUserIdMissingException
import me.topilov.springplayground.profile.exception.ProfileUserLinkMissingException

fun UserProfile.toResponse(): ProfileResponse {
    val persistedUser = user ?: throw ProfileUserLinkMissingException()
    return ProfileResponse(
        id = id ?: throw PersistedProfileIdMissingException(),
        userId = persistedUser.id ?: throw PersistedProfileUserIdMissingException(),
        username = persistedUser.username,
        email = persistedUser.email,
        role = persistedUser.role.name,
        displayName = displayName,
        bio = bio,
    )
}
