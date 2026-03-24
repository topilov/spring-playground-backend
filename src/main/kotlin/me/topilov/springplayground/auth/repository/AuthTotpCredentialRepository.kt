package me.topilov.springplayground.auth.repository

import me.topilov.springplayground.auth.domain.AuthTotpCredential
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface AuthTotpCredentialRepository : JpaRepository<AuthTotpCredential, Long> {
    fun findByUserId(userId: Long): Optional<AuthTotpCredential>
    fun findByUserIdAndEnabledAtIsNotNull(userId: Long): Optional<AuthTotpCredential>
    fun existsByUserIdAndEnabledAtIsNotNull(userId: Long): Boolean
}
