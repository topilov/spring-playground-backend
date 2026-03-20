package me.topilov.springplayground.auth.passkey.ceremony

interface PasskeyCeremonyStore {
    fun createCeremonyId(): String

    fun saveRegistration(ceremony: RegistrationPasskeyCeremony)

    fun findRegistration(ceremonyId: String): RegistrationPasskeyCeremony?

    fun invalidateRegistration(ceremonyId: String)

    fun saveAuthentication(ceremony: AuthenticationPasskeyCeremony)

    fun findAuthentication(ceremonyId: String): AuthenticationPasskeyCeremony?

    fun invalidateAuthentication(ceremonyId: String)
}

data class RegistrationPasskeyCeremony(
    val ceremonyId: String,
    val userId: Long,
    val requestJson: String,
    val nickname: String?,
)

data class AuthenticationPasskeyCeremony(
    val ceremonyId: String,
    val requestJson: String,
)
