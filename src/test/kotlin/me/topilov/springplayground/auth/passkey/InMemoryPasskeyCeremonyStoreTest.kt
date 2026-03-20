package me.topilov.springplayground.auth.passkey

import me.topilov.springplayground.auth.passkey.ceremony.AuthenticationPasskeyCeremony
import me.topilov.springplayground.auth.passkey.ceremony.RegistrationPasskeyCeremony
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InMemoryPasskeyCeremonyStoreTest {
    private val store = InMemoryPasskeyCeremonyStore()

    @Test
    fun `registration and authentication ceremonies are stored independently and can be invalidated`() {
        val registration = RegistrationPasskeyCeremony(
            ceremonyId = "registration-1",
            userId = 1,
            requestJson = """{"challenge":"register"}""",
            nickname = "Laptop",
        )
        val authentication = AuthenticationPasskeyCeremony(
            ceremonyId = "authentication-1",
            requestJson = """{"challenge":"login"}""",
        )

        store.saveRegistration(registration)
        store.saveAuthentication(authentication)

        assertThat(store.findRegistration("registration-1")).isEqualTo(registration)
        assertThat(store.findAuthentication("authentication-1")).isEqualTo(authentication)

        store.invalidateRegistration("registration-1")
        store.invalidateAuthentication("authentication-1")

        assertThat(store.findRegistration("registration-1")).isNull()
        assertThat(store.findAuthentication("authentication-1")).isNull()
    }
}
