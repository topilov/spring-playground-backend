package me.topilov.springplayground.auth.passkey

import me.topilov.springplayground.auth.passkey.ceremony.PasskeyCeremonyStore
import me.topilov.springplayground.auth.passkey.webauthn.PasskeyWebAuthnService
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

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
