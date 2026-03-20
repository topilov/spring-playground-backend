package me.topilov.springplayground.auth.reset

interface PasswordResetTokenStore {
    fun createToken(): String
    fun activateToken(userId: Long, rawToken: String)
    fun findUserId(rawToken: String): Long?
    fun invalidateToken(rawToken: String)
    fun invalidateAllTokensForUser(userId: Long)
}
