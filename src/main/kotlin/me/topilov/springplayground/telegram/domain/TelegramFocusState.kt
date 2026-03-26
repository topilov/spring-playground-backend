package me.topilov.springplayground.telegram.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "telegram_focus_state")
class TelegramFocusState(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "focus_mode", nullable = false, length = 64)
    var focusMode: TelegramFocusMode,
    @Column(nullable = false)
    var active: Boolean = false,
    @Column(name = "last_activated_at")
    var lastActivatedAt: Instant? = null,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH,
) {
    @PrePersist
    fun prePersist() {
        if (updatedAt == Instant.EPOCH) {
            updatedAt = Instant.now()
        }
    }

    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now()
    }
}
