package me.topilov.springplayground.auth.service

import java.time.Instant

data class StoredTwoFactorLoginChallenge(
    val loginChallengeId: String,
    val userId: Long,
    val expiresAt: Instant,
)
