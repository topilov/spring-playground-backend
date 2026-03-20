package me.topilov.springplayground.auth.repository

import me.topilov.springplayground.auth.domain.AuthUser
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface AuthUserRepository : JpaRepository<AuthUser, Long> {
    fun findByUsernameIgnoreCaseOrEmailIgnoreCase(username: String, email: String): Optional<AuthUser>
    fun existsByUsernameIgnoreCase(username: String): Boolean
    fun existsByEmailIgnoreCase(email: String): Boolean
    fun findByEmailIgnoreCase(email: String): Optional<AuthUser>
    fun findByWebauthnUserHandle(webauthnUserHandle: String): Optional<AuthUser>
}
