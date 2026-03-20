package me.topilov.springplayground.auth.passkey.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import me.topilov.springplayground.auth.domain.AuthUser
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "auth_passkey_credential")
class PasskeyCredential(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: AuthUser,
    @Column(name = "credential_id", nullable = false, unique = true, length = 1024)
    var credentialId: String,
    @Column(name = "public_key_cose", nullable = false, columnDefinition = "bytea")
    var publicKeyCose: ByteArray,
    @Column(name = "signature_count", nullable = false)
    var signatureCount: Long,
    @Column(name = "aaguid")
    var aaguid: UUID? = null,
    @Column(name = "transports", length = 255)
    var transports: String? = null,
    @Column(name = "nickname", nullable = false, length = 100)
    var nickname: String,
    @Column(name = "authenticator_attachment", length = 32)
    var authenticatorAttachment: String? = null,
    @Column(name = "discoverable")
    var discoverable: Boolean? = null,
    @Column(name = "backup_eligible")
    var backupEligible: Boolean? = null,
    @Column(name = "backup_state")
    var backupState: Boolean? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.EPOCH,
    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH,
) {
    @PrePersist
    fun prePersist() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now()
    }
}
