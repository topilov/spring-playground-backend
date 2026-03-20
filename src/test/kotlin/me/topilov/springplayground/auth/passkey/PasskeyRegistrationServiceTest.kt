package me.topilov.springplayground.auth.passkey

import me.topilov.springplayground.auth.domain.AuthUser
import me.topilov.springplayground.auth.passkey.ceremony.RegistrationPasskeyCeremony
import me.topilov.springplayground.auth.passkey.exception.DuplicatePasskeyCredentialException
import me.topilov.springplayground.auth.passkey.repository.PasskeyCredentialRepository
import me.topilov.springplayground.auth.passkey.service.PasskeyManagementService
import me.topilov.springplayground.auth.passkey.service.PasskeyRegistrationService
import me.topilov.springplayground.auth.passkey.webauthn.FinishedPasskeyRegistration
import me.topilov.springplayground.auth.passkey.webauthn.PasskeyUserIdentity
import me.topilov.springplayground.auth.passkey.webauthn.PasskeyWebAuthnService
import me.topilov.springplayground.auth.passkey.webauthn.StartedPasskeyAuthentication
import me.topilov.springplayground.auth.passkey.webauthn.StartedPasskeyRegistration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Optional

class PasskeyRegistrationServiceTest {
    private val authUserRepository = mock(me.topilov.springplayground.auth.repository.AuthUserRepository::class.java)
    private val passkeyCredentialRepository = mock(PasskeyCredentialRepository::class.java)
    private val passkeyCeremonyStore = InMemoryPasskeyCeremonyStore()
    private val passkeyWebAuthnService = FakeUnitPasskeyWebAuthnService()
    private val service = PasskeyRegistrationService(
        authUserRepository = authUserRepository,
        passkeyCredentialRepository = passkeyCredentialRepository,
        passkeyCeremonyStore = passkeyCeremonyStore,
        passkeyWebAuthnService = passkeyWebAuthnService,
        passkeyManagementService = PasskeyManagementService(passkeyCredentialRepository),
    )

    @Test
    fun `finish registration rejects duplicate credential before persistence`() {
        val userId = 1L
        val ceremonyId = "registration-ceremony"
        passkeyCeremonyStore.saveRegistration(
            RegistrationPasskeyCeremony(
                ceremonyId = ceremonyId,
                userId = userId,
                requestJson = """{"challenge":"abc"}""",
                nickname = "Laptop",
            ),
        )
        passkeyWebAuthnService.finishedRegistration = FinishedPasskeyRegistration(
            credentialId = "duplicate-passkey",
            publicKeyCose = byteArrayOf(1, 2, 3),
            signatureCount = 0,
        )
        `when`(passkeyCredentialRepository.existsByCredentialId("duplicate-passkey")).thenReturn(true)

        assertThrows(DuplicatePasskeyCredentialException::class.java) {
            service.finishRegistration(
                userId = userId,
                request = me.topilov.springplayground.auth.passkey.dto.PasskeyRegistrationVerifyRequest(
                    ceremonyId = ceremonyId,
                    credential = mapOf("credentialId" to "duplicate-passkey"),
                ),
            )
        }

        verify(passkeyCredentialRepository, never()).save(org.mockito.ArgumentMatchers.any())
        assertThat(passkeyCeremonyStore.findRegistration(ceremonyId)).isNotNull()
    }

    @Test
    fun `start registration assigns a stable user handle when one is missing`() {
        val user = AuthUser(
            id = 1,
            username = "demo",
            email = "demo@example.com",
            passwordHash = "hash",
            webauthnUserHandle = null,
        )
        `when`(authUserRepository.findById(1L)).thenReturn(Optional.of(user))
        `when`(passkeyCredentialRepository.findAllByUserIdOrderByCreatedAtDesc(1L)).thenReturn(emptyList())
        passkeyCeremonyStore.nextCeremonyId = "ceremony-1"
        passkeyWebAuthnService.startedRegistration = StartedPasskeyRegistration(
            requestJson = """{"challenge":"abc"}""",
            publicKeyJson = mapOf("challenge" to "abc"),
        )

        service.startRegistration(
            userId = 1L,
            request = me.topilov.springplayground.auth.passkey.dto.PasskeyRegistrationOptionsRequest(nickname = "Laptop"),
        )

        assertThat(user.webauthnUserHandle).isNotBlank()
        val storedCeremony = passkeyCeremonyStore.findRegistration("ceremony-1")
        assertThat(storedCeremony).isNotNull
        assertThat(storedCeremony?.userId).isEqualTo(1L)
        assertThat(storedCeremony?.nickname).isEqualTo("Laptop")
    }
}

private class FakeUnitPasskeyWebAuthnService : PasskeyWebAuthnService {
    var startedRegistration: StartedPasskeyRegistration = StartedPasskeyRegistration(
        requestJson = """{"challenge":"default"}""",
        publicKeyJson = mapOf("challenge" to "default"),
    )
    var finishedRegistration: FinishedPasskeyRegistration = FinishedPasskeyRegistration(
        credentialId = "default-credential",
        publicKeyCose = byteArrayOf(1),
        signatureCount = 0,
    )

    override fun beginRegistration(user: PasskeyUserIdentity, existingCredentialIds: List<String>): StartedPasskeyRegistration =
        startedRegistration

    override fun finishRegistration(requestJson: String, credentialJson: String): FinishedPasskeyRegistration =
        finishedRegistration

    override fun beginAuthentication(): StartedPasskeyAuthentication {
        throw UnsupportedOperationException("Authentication is not used in this test")
    }

    override fun finishAuthentication(requestJson: String, credentialJson: String) =
        throw UnsupportedOperationException("Authentication is not used in this test")
}
