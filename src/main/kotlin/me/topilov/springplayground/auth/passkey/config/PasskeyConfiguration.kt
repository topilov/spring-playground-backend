package me.topilov.springplayground.auth.passkey.config

import com.yubico.webauthn.CredentialRepository
import com.yubico.webauthn.RegisteredCredential
import com.yubico.webauthn.RelyingParty
import com.yubico.webauthn.data.ByteArray
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor
import com.yubico.webauthn.data.RelyingPartyIdentity
import me.topilov.springplayground.auth.passkey.repository.PasskeyCredentialRepository
import me.topilov.springplayground.auth.repository.AuthUserRepository
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.Optional

@Configuration
@EnableConfigurationProperties(PasskeyProperties::class)
class PasskeyConfiguration {
    @Bean
    fun credentialRepository(
        authUserRepository: AuthUserRepository,
        passkeyCredentialRepository: PasskeyCredentialRepository,
    ): CredentialRepository = object : CredentialRepository {
        override fun getCredentialIdsForUsername(username: String): Set<PublicKeyCredentialDescriptor> {
            val user = authUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(username, username).orElse(null) ?: return emptySet()
            val userId = user.id ?: return emptySet()
            return passkeyCredentialRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                .map { credential ->
                    val builder = PublicKeyCredentialDescriptor.builder()
                        .id(ByteArray.fromBase64Url(credential.credentialId))
                    if (!credential.transports.isNullOrBlank()) {
                        builder.transports(
                            credential.transports
                                ?.split(',')
                                ?.map(String::trim)
                                ?.filter(String::isNotBlank)
                                ?.map { com.yubico.webauthn.data.AuthenticatorTransport.of(it) }
                                ?.toSet()
                                .orEmpty(),
                        )
                    }
                    builder.build()
                }
                .toSet()
        }

        override fun getUserHandleForUsername(username: String): Optional<ByteArray> =
            authUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(username, username)
                .flatMap { Optional.ofNullable(it.webauthnUserHandle) }
                .map(ByteArray::fromBase64Url)

        override fun getUsernameForUserHandle(userHandle: ByteArray): Optional<String> =
            authUserRepository.findByWebauthnUserHandle(userHandle.base64Url)
                .map { it.username }

        override fun lookup(credentialId: ByteArray, userHandle: ByteArray): Optional<RegisteredCredential> =
            passkeyCredentialRepository.findByCredentialId(credentialId.base64Url)
                .filter { it.user.webauthnUserHandle == userHandle.base64Url }
                .map { credential ->
                    RegisteredCredential.builder()
                        .credentialId(ByteArray.fromBase64Url(credential.credentialId))
                        .userHandle(ByteArray.fromBase64Url(requireNotNull(credential.user.webauthnUserHandle)))
                        .publicKeyCose(ByteArray(credential.publicKeyCose))
                        .signatureCount(credential.signatureCount)
                        .backupEligible(credential.backupEligible)
                        .backupState(credential.backupState)
                        .build()
                }

        override fun lookupAll(credentialId: ByteArray): Set<RegisteredCredential> =
            passkeyCredentialRepository.findByCredentialId(credentialId.base64Url)
                .map { credential ->
                    setOf(
                        RegisteredCredential.builder()
                            .credentialId(ByteArray.fromBase64Url(credential.credentialId))
                            .userHandle(ByteArray.fromBase64Url(requireNotNull(credential.user.webauthnUserHandle)))
                            .publicKeyCose(ByteArray(credential.publicKeyCose))
                            .signatureCount(credential.signatureCount)
                            .backupEligible(credential.backupEligible)
                            .backupState(credential.backupState)
                            .build(),
                    )
                }
                .orElse(emptySet())
    }

    @Bean
    fun relyingParty(
        passkeyProperties: PasskeyProperties,
        credentialRepository: CredentialRepository,
    ): RelyingParty = RelyingParty.builder()
        .identity(
            RelyingPartyIdentity.builder()
                .id(passkeyProperties.rpId)
                .name(passkeyProperties.rpName)
                .build(),
        )
        .credentialRepository(credentialRepository)
        .origins(passkeyProperties.origins.toSet())
        .build()
}
