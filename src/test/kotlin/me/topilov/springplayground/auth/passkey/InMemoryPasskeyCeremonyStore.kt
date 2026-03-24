package me.topilov.springplayground.auth.passkey

import me.topilov.springplayground.auth.passkey.ceremony.AuthenticationPasskeyCeremony
import me.topilov.springplayground.auth.passkey.ceremony.PasskeyCeremonyStore
import me.topilov.springplayground.auth.passkey.ceremony.RegistrationPasskeyCeremony
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

class InMemoryPasskeyCeremonyStore : PasskeyCeremonyStore {
    private val registrations = ConcurrentHashMap<String, RegistrationPasskeyCeremony>()
    private val authentications = ConcurrentHashMap<String, AuthenticationPasskeyCeremony>()
    var nextCeremonyId: String? = null

    override fun createCeremonyId(): String {
        nextCeremonyId?.let {
            nextCeremonyId = null
            return it
        }
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    override fun saveRegistration(ceremony: RegistrationPasskeyCeremony) {
        registrations[ceremony.ceremonyId] = ceremony
    }

    override fun findRegistration(ceremonyId: String): RegistrationPasskeyCeremony? = registrations[ceremonyId]

    override fun invalidateRegistration(ceremonyId: String) {
        registrations.remove(ceremonyId)
    }

    override fun saveAuthentication(ceremony: AuthenticationPasskeyCeremony) {
        authentications[ceremony.ceremonyId] = ceremony
    }

    override fun findAuthentication(ceremonyId: String): AuthenticationPasskeyCeremony? = authentications[ceremonyId]

    override fun invalidateAuthentication(ceremonyId: String) {
        authentications.remove(ceremonyId)
    }

    fun clear() {
        registrations.clear()
        authentications.clear()
    }

    companion object {
        private val secureRandom = SecureRandom()
    }
}
