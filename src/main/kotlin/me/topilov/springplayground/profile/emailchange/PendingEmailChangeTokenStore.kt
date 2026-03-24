package me.topilov.springplayground.profile.emailchange

interface PendingEmailChangeTokenStore {
    fun createToken(): String
    fun activateToken(userId: Long, newEmail: String, rawToken: String)
    fun findPendingChange(rawToken: String): PendingEmailChange?
    fun invalidateToken(rawToken: String)
    fun invalidateAllTokensForUser(userId: Long)
}
