package me.topilov.springplayground.auth.service

import java.time.Instant

interface TwoFactorLoginChallengeStore {
    fun createChallengeId(): String
    fun save(challenge: StoredTwoFactorLoginChallenge)
    fun find(loginChallengeId: String): StoredTwoFactorLoginChallenge?
    fun consume(loginChallengeId: String): StoredTwoFactorLoginChallenge?
}

data class StoredTwoFactorLoginChallenge(
    val loginChallengeId: String,
    val userId: Long,
    val expiresAt: Instant,
)
