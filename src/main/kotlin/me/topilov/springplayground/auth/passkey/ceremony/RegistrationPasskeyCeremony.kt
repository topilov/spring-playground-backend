package me.topilov.springplayground.auth.passkey.ceremony

data class RegistrationPasskeyCeremony(
    val ceremonyId: String,
    val userId: Long,
    val requestJson: String,
    val nickname: String?,
)
