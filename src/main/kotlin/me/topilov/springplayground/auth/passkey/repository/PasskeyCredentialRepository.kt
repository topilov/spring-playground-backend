package me.topilov.springplayground.auth.passkey.repository

import me.topilov.springplayground.auth.passkey.domain.PasskeyCredential
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface PasskeyCredentialRepository : JpaRepository<PasskeyCredential, Long> {
    fun existsByCredentialId(credentialId: String): Boolean
    fun findByCredentialId(credentialId: String): Optional<PasskeyCredential>
    fun findAllByUserIdOrderByCreatedAtDesc(userId: Long): List<PasskeyCredential>
    fun findByIdAndUserId(id: Long, userId: Long): Optional<PasskeyCredential>
    fun countByUserId(userId: Long): Long
}
