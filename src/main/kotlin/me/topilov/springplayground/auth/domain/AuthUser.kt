package me.topilov.springplayground.auth.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import me.topilov.springplayground.profile.domain.UserProfile
import java.time.Instant

@Entity
@Table(name = "auth_user")
class AuthUser(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(nullable = false, unique = true, length = 64)
    var username: String = "",
    @Column(nullable = false, unique = true, length = 255)
    var email: String = "",
    @Column(name = "password_hash", nullable = false, length = 100)
    var passwordHash: String = "",
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var role: AuthRole = AuthRole.USER,
    @Column(nullable = false)
    var enabled: Boolean = true,
    @Column(name = "email_verified", nullable = false)
    var emailVerified: Boolean = false,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.EPOCH,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH,
    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
    var profile: UserProfile? = null,
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
