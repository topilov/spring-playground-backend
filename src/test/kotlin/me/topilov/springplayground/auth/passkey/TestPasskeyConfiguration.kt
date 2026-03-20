package me.topilov.springplayground.auth.passkey

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import me.topilov.springplayground.auth.passkey.ceremony.AuthenticationPasskeyCeremony
import me.topilov.springplayground.auth.passkey.ceremony.PasskeyCeremonyStore
import me.topilov.springplayground.auth.passkey.ceremony.RegistrationPasskeyCeremony
import me.topilov.springplayground.auth.passkey.webauthn.FinishedPasskeyAuthentication
import me.topilov.springplayground.auth.passkey.webauthn.FinishedPasskeyRegistration
import me.topilov.springplayground.auth.passkey.webauthn.PasskeyUserIdentity
import me.topilov.springplayground.auth.passkey.webauthn.PasskeyWebAuthnService
import me.topilov.springplayground.auth.passkey.webauthn.StartedPasskeyAuthentication
import me.topilov.springplayground.auth.passkey.webauthn.StartedPasskeyRegistration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

@TestConfiguration
class TestPasskeyConfiguration {
    @Bean
    fun inMemoryPasskeyCeremonyStore(): InMemoryPasskeyCeremonyStore = InMemoryPasskeyCeremonyStore()

    @Bean
    @Primary
    fun passkeyCeremonyStore(inMemoryPasskeyCeremonyStore: InMemoryPasskeyCeremonyStore): PasskeyCeremonyStore =
        inMemoryPasskeyCeremonyStore

    @Bean
    @Primary
    fun passkeyWebAuthnService(): PasskeyWebAuthnService = FakePasskeyWebAuthnService()
}

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

class FakePasskeyWebAuthnService : PasskeyWebAuthnService {
    private val objectMapper = jacksonObjectMapper()

    override fun beginRegistration(
        user: PasskeyUserIdentity,
        existingCredentialIds: List<String>,
    ): StartedPasskeyRegistration {
        val requestJson = objectMapper.writeValueAsString(
            mapOf(
                "userHandle" to user.userHandle,
                "username" to user.username,
                "existingCredentialIds" to existingCredentialIds,
            ),
        )
        return StartedPasskeyRegistration(
            requestJson = requestJson,
            publicKeyJson = mapOf("challenge" to "test-registration-challenge"),
        )
    }

    override fun finishRegistration(requestJson: String, credentialJson: String): FinishedPasskeyRegistration {
        val credential = objectMapper.readTree(credentialJson)
        return FinishedPasskeyRegistration(
            credentialId = credential["credentialId"].asText(),
            publicKeyCose = credential["credentialId"].asText().toByteArray(),
            signatureCount = 0,
            transports = listOf("internal"),
            authenticatorAttachment = "platform",
            discoverable = true,
        )
    }

    override fun beginAuthentication(): StartedPasskeyAuthentication =
        StartedPasskeyAuthentication(
            requestJson = """{"challenge":"test-login-challenge"}""",
            publicKeyJson = mapOf("challenge" to "test-login-challenge"),
        )

    override fun finishAuthentication(requestJson: String, credentialJson: String): FinishedPasskeyAuthentication {
        val credential = objectMapper.readTree(credentialJson)
        return FinishedPasskeyAuthentication(
            credentialId = credential["credentialId"].asText(),
            signatureCount = 1,
            backupEligible = true,
            backupState = true,
        )
    }
}
