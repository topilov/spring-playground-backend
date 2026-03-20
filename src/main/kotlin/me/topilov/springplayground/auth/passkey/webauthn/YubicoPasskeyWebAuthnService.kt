package me.topilov.springplayground.auth.passkey.webauthn

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.yubico.webauthn.AssertionRequest
import com.yubico.webauthn.FinishAssertionOptions
import com.yubico.webauthn.FinishRegistrationOptions
import com.yubico.webauthn.RelyingParty
import com.yubico.webauthn.StartAssertionOptions
import com.yubico.webauthn.StartRegistrationOptions
import com.yubico.webauthn.data.AuthenticatorAttachment
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria
import com.yubico.webauthn.data.ByteArray
import com.yubico.webauthn.data.PublicKeyCredential
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions
import com.yubico.webauthn.data.ResidentKeyRequirement
import com.yubico.webauthn.data.UserIdentity
import com.yubico.webauthn.data.UserVerificationRequirement
import me.topilov.springplayground.auth.passkey.config.PasskeyProperties
import org.springframework.stereotype.Service

@Service
class YubicoPasskeyWebAuthnService(
    private val relyingParty: RelyingParty,
    private val passkeyProperties: PasskeyProperties,
) : PasskeyWebAuthnService {
    override fun beginRegistration(user: PasskeyUserIdentity, existingCredentialIds: List<String>): StartedPasskeyRegistration {
        val registrationRequest = relyingParty.startRegistration(
            StartRegistrationOptions.builder()
                .user(
                    UserIdentity.builder()
                        .name(user.username)
                        .displayName(user.displayName)
                        .id(ByteArray.fromBase64Url(user.userHandle))
                        .build(),
                )
                .authenticatorSelection(
                    AuthenticatorSelectionCriteria.builder()
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .userVerification(UserVerificationRequirement.PREFERRED)
                        .build(),
                )
                .timeout(passkeyProperties.ceremonyTtl.toMillis())
                .build(),
        )

        return StartedPasskeyRegistration(
            requestJson = registrationRequest.toJson(),
            credentialCreationJson = registrationRequest.toCredentialsCreateJson(),
        )
    }

    override fun finishRegistration(requestJson: String, credentialJson: String): FinishedPasskeyRegistration {
        val request = PublicKeyCredentialCreationOptions.fromJson(requestJson)
        val response = PublicKeyCredential.parseRegistrationResponseJson(credentialJson)
        val result = relyingParty.finishRegistration(
            FinishRegistrationOptions.builder()
                .request(request)
                .response(response)
                .build(),
        )

        return FinishedPasskeyRegistration(
            credentialId = result.keyId.id.base64Url,
            publicKeyCose = result.publicKeyCose.bytes,
            signatureCount = result.signatureCount,
            transports = result.keyId.transports.map { transports ->
                transports.map { it.id }.sorted()
            }.orElse(emptyList()),
            authenticatorAttachment = response.authenticatorAttachment.map(::mapAttachment).orElse(null),
            discoverable = result.isDiscoverable().orElse(null),
            backupEligible = result.isBackupEligible(),
            backupState = result.isBackedUp(),
        )
    }

    override fun beginAuthentication(): StartedPasskeyAuthentication {
        val assertionRequest = relyingParty.startAssertion(
            StartAssertionOptions.builder()
                .userVerification(UserVerificationRequirement.PREFERRED)
                .timeout(passkeyProperties.ceremonyTtl.toMillis())
                .build(),
        )

        return StartedPasskeyAuthentication(
            requestJson = assertionRequest.toJson(),
            credentialRequestJson = assertionRequest.toCredentialsGetJson(),
        )
    }

    override fun finishAuthentication(requestJson: String, credentialJson: String): FinishedPasskeyAuthentication {
        val request = AssertionRequest.fromJson(requestJson)
        val response = PublicKeyCredential.parseAssertionResponseJson(credentialJson)
        val result = relyingParty.finishAssertion(
            FinishAssertionOptions.builder()
                .request(request)
                .response(response)
                .build(),
        )

        return FinishedPasskeyAuthentication(
            credentialId = result.credentialId.base64Url,
            signatureCount = result.signatureCount,
            backupEligible = result.isBackupEligible(),
            backupState = result.isBackedUp(),
        )
    }

    private fun mapAttachment(attachment: AuthenticatorAttachment): String = when (attachment) {
        AuthenticatorAttachment.PLATFORM -> "platform"
        AuthenticatorAttachment.CROSS_PLATFORM -> "cross-platform"
    }
}
