package me.topilov.springplayground.telegram.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "telegram_automation_token")
class TelegramAutomationToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "user_id", nullable = false, unique = true)
    var userId: Long,
    @Column(name = "token_hash", nullable = false, length = 64)
    var tokenHash: String,
    @Column(name = "token_hint", nullable = false, length = 32)
    var tokenHint: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.EPOCH,
    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null,
    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,
) {
    @PrePersist
    fun prePersist() {
        if (createdAt == Instant.EPOCH) {
            createdAt = Instant.now()
        }
    }
}
