package me.topilov.springplayground.auth.security

import me.topilov.springplayground.auth.exception.AuthUserNotFoundException
import me.topilov.springplayground.auth.exception.PersistedAuthUserIdMissingException
import me.topilov.springplayground.auth.repository.AuthUserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.stereotype.Service

@Service
class AppUserDetailsService(
    private val authUserRepository: AuthUserRepository,
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = authUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(username, username)
            .orElseThrow { AuthUserNotFoundException(username) }

        return AppUserPrincipal(
            id = user.id ?: throw PersistedAuthUserIdMissingException(),
            usernameValue = user.username,
            email = user.email,
            passwordHash = user.passwordHash,
            role = user.role,
            enabledValue = user.enabled,
        )
    }
}
