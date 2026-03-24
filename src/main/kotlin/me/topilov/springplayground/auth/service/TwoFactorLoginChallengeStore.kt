package me.topilov.springplayground.auth.service

interface TwoFactorLoginChallengeStore {
    fun createChallengeId(): String
    fun save(challenge: StoredTwoFactorLoginChallenge)
    fun find(loginChallengeId: String): StoredTwoFactorLoginChallenge?
    fun consume(loginChallengeId: String): StoredTwoFactorLoginChallenge?
}
