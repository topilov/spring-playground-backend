package me.topilov.springplayground.auth.security

import me.topilov.springplayground.auth.exception.AuthUserNotFoundException
import me.topilov.springplayground.auth.repository.AuthUserRepository
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import java.util.Optional

class AppUserDetailsServiceTest {
    private val authUserRepository = mock(AuthUserRepository::class.java)
    private val service = AppUserDetailsService(authUserRepository)

    @Test
    fun `loadUserByUsername throws custom exception when user is absent`() {
        given(authUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("missing", "missing"))
            .willReturn(Optional.empty())

        assertThrows(AuthUserNotFoundException::class.java) {
            service.loadUserByUsername("missing")
        }
    }
}
