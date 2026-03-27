package me.topilov.springplayground.auth.application

import me.topilov.springplayground.auth.domain.AuthRole
import me.topilov.springplayground.auth.domain.AuthUser
import me.topilov.springplayground.auth.repository.AuthUserRepository
import me.topilov.springplayground.profile.domain.UserProfile
import me.topilov.springplayground.profile.repository.UserProfileRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional

class LocalDemoUserInitializerTest {
    private val authUserRepository = mock(AuthUserRepository::class.java)
    private val userProfileRepository = mock(UserProfileRepository::class.java)
    private val passwordEncoder = mock(PasswordEncoder::class.java)

    private val properties = LocalDemoUserProperties(
        username = "demo",
        email = "demo@example.com",
        password = "demo-password",
        displayName = "Demo User",
        bio = "Session-backed example profile",
    )

    private val initializer = LocalDemoUserInitializer(
        properties = properties,
        authUserRepository = authUserRepository,
        userProfileRepository = userProfileRepository,
        passwordEncoder = passwordEncoder,
    )

    @Test
    fun `ensure demo user creates local account and profile when missing`() {
        `when`(authUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("demo", "demo@example.com"))
            .thenReturn(Optional.empty())
        `when`(passwordEncoder.encode("demo-password")).thenReturn("encoded-demo-password")
        `when`(authUserRepository.save(org.mockito.ArgumentMatchers.any(AuthUser::class.java))).thenAnswer { invocation ->
            val saved = invocation.getArgument<AuthUser>(0)
            saved.id = 1L
            saved
        }

        initializer.ensureDemoUser()

        val savedUser = ArgumentCaptor.forClass(AuthUser::class.java)
        verify(authUserRepository).save(savedUser.capture())
        assertThat(savedUser.value.username).isEqualTo("demo")
        assertThat(savedUser.value.email).isEqualTo("demo@example.com")
        assertThat(savedUser.value.passwordHash).isEqualTo("encoded-demo-password")
        assertThat(savedUser.value.role).isEqualTo(AuthRole.USER)
        assertThat(savedUser.value.enabled).isTrue()
        assertThat(savedUser.value.emailVerified).isTrue()

        val savedProfile = ArgumentCaptor.forClass(UserProfile::class.java)
        verify(userProfileRepository).save(savedProfile.capture())
        assertThat(savedProfile.value.user).isSameAs(savedUser.value)
        assertThat(savedProfile.value.displayName).isEqualTo("Demo User")
        assertThat(savedProfile.value.bio).isEqualTo("Session-backed example profile")
    }

    @Test
    fun `ensure demo user repairs existing local account and missing profile`() {
        val existingUser = AuthUser(
            id = 1L,
            username = "someone-else",
            email = "old@example.com",
            passwordHash = "old-password-hash",
            role = AuthRole.USER,
            enabled = false,
            emailVerified = false,
        )
        `when`(authUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("demo", "demo@example.com"))
            .thenReturn(Optional.of(existingUser))
        `when`(userProfileRepository.findByUserId(1L)).thenReturn(Optional.empty())
        `when`(passwordEncoder.encode("demo-password")).thenReturn("encoded-demo-password")

        initializer.ensureDemoUser()

        verify(authUserRepository).save(existingUser)
        assertThat(existingUser.username).isEqualTo("demo")
        assertThat(existingUser.email).isEqualTo("demo@example.com")
        assertThat(existingUser.passwordHash).isEqualTo("encoded-demo-password")
        assertThat(existingUser.enabled).isTrue()
        assertThat(existingUser.emailVerified).isTrue()

        val savedProfile = ArgumentCaptor.forClass(UserProfile::class.java)
        verify(userProfileRepository).save(savedProfile.capture())
        assertThat(savedProfile.value.user).isSameAs(existingUser)
        assertThat(savedProfile.value.displayName).isEqualTo("Demo User")
        assertThat(savedProfile.value.bio).isEqualTo("Session-backed example profile")
        verify(passwordEncoder, times(1)).encode("demo-password")
    }
}
