package me.topilov.springplayground.profile.mapper

import me.topilov.springplayground.auth.domain.AuthRole
import me.topilov.springplayground.auth.domain.AuthUser
import me.topilov.springplayground.profile.domain.UserProfile
import me.topilov.springplayground.profile.exception.ProfileUserLinkMissingException
import me.topilov.springplayground.profile.exception.PersistedProfileIdMissingException
import me.topilov.springplayground.profile.exception.PersistedProfileUserIdMissingException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class UserProfileResponseMapperTest {
    @Test
    fun `toResponse maps persisted profile fields`() {
        val user = AuthUser(
            id = 7L,
            username = "demo",
            email = "demo@example.com",
            role = AuthRole.USER,
        )
        val profile = UserProfile(
            id = 11L,
            user = user,
            displayName = "Demo User",
            bio = "Hello",
        )

        val response = profile.toResponse()

        assertEquals(11L, response.id)
        assertEquals(7L, response.userId)
        assertEquals("demo", response.username)
        assertEquals("demo@example.com", response.email)
        assertEquals("USER", response.role)
        assertEquals("Demo User", response.displayName)
        assertEquals("Hello", response.bio)
    }

    @Test
    fun `toResponse throws when profile is not linked to user`() {
        val profile = UserProfile(
            id = 11L,
            user = null,
            displayName = "Demo User",
            bio = "Hello",
        )

        assertThrows(ProfileUserLinkMissingException::class.java) {
            profile.toResponse()
        }
    }

    @Test
    fun `toResponse throws when profile id is missing`() {
        val profile = UserProfile(
            id = null,
            user = AuthUser(
                id = 7L,
                username = "demo",
                email = "demo@example.com",
                role = AuthRole.USER,
            ),
            displayName = "Demo User",
            bio = "Hello",
        )

        assertThrows(PersistedProfileIdMissingException::class.java) {
            profile.toResponse()
        }
    }

    @Test
    fun `toResponse throws when linked user id is missing`() {
        val profile = UserProfile(
            id = 11L,
            user = AuthUser(
                id = null,
                username = "demo",
                email = "demo@example.com",
                role = AuthRole.USER,
            ),
            displayName = "Demo User",
            bio = "Hello",
        )

        assertThrows(PersistedProfileUserIdMissingException::class.java) {
            profile.toResponse()
        }
    }
}
