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
@Table(name = "telegram_account_connection")
class TelegramAccountConnection(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "user_id", nullable = false, unique = true)
    var userId: Long,
    @Column(name = "telegram_user_id")
    var telegramUserId: Long? = null,
    @Column(name = "telegram_phone_number", length = 32)
    var telegramPhoneNumber: String? = null,
    @Column(name = "telegram_username", length = 255)
    var telegramUsername: String? = null,
    @Column(name = "telegram_display_name", length = 255)
    var telegramDisplayName: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 32)
    var connectionStatus: TelegramConnectionStatus = TelegramConnectionStatus.DISCONNECTED,
    @Column(nullable = false)
    var premium: Boolean = false,
    @Column(name = "session_directory_key", length = 255)
    var sessionDirectoryKey: String? = null,
    @Column(name = "session_database_key_ciphertext")
    var sessionDatabaseKeyCiphertext: String? = null,
    @Column(name = "default_emoji_status_document_id", length = 64)
    var defaultEmojiStatusDocumentId: String? = null,
    @Column(name = "active_mode_id")
    var activeModeId: Long? = null,
    @Column(name = "last_sync_error_code", length = 64)
    var lastSyncErrorCode: String? = null,
    @Column(name = "last_sync_error_message", length = 500)
    var lastSyncErrorMessage: String? = null,
    @Column(name = "last_synced_at")
    var lastSyncedAt: Instant? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.EPOCH,
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
