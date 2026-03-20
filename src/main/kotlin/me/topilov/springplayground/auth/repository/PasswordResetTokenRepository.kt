package me.topilov.springplayground.auth.repository

import me.topilov.springplayground.auth.domain.PasswordResetToken
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, Long> {
    fun findByTokenHashAndUsedAtIsNull(tokenHash: String): Optional<PasswordResetToken>
    fun findAllByUserIdAndUsedAtIsNull(userId: Long): List<PasswordResetToken>
}
