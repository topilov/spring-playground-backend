package me.topilov.springplayground.profile.emailchange

data class PendingEmailChange(
    val userId: Long,
    val newEmail: String,
)
