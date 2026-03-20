package me.topilov.springplayground.auth.verification

interface EmailVerificationTokenStore {
    fun createToken(): String

    fun activateToken(userId: Long, rawToken: String)

    fun findUserId(rawToken: String): Long?

    fun invalidateToken(rawToken: String)
}
