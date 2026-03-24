package me.topilov.springplayground.auth.repository

import me.topilov.springplayground.auth.domain.AuthTotpBackupCode
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface AuthTotpBackupCodeRepository : JpaRepository<AuthTotpBackupCode, Long> {
    fun findAllByCredentialIdAndUsedAtIsNullOrderByIdAsc(credentialId: Long): List<AuthTotpBackupCode>
    fun countByCredentialIdAndUsedAtIsNull(credentialId: Long): Long
    fun deleteAllByCredentialId(credentialId: Long)

    @Modifying
    @Query(
        """
        update AuthTotpBackupCode code
           set code.usedAt = :usedAt
         where code.id = :id
           and code.usedAt is null
        """,
    )
    fun markUsed(
        @Param("id") id: Long,
        @Param("usedAt") usedAt: Instant,
    ): Int
}
