package me.topilov.springplayground.auth.passkey

import me.topilov.springplayground.auth.passkey.webauthn.FinishedPasskeyAuthentication
import me.topilov.springplayground.auth.passkey.webauthn.FinishedPasskeyRegistration
import me.topilov.springplayground.auth.passkey.webauthn.PasskeyUserIdentity
import me.topilov.springplayground.auth.passkey.webauthn.PasskeyWebAuthnService
import me.topilov.springplayground.auth.passkey.webauthn.StartedPasskeyAuthentication
import me.topilov.springplayground.auth.passkey.webauthn.StartedPasskeyRegistration
import me.topilov.springplayground.support.TestObjectMapper

class FakePasskeyWebAuthnService : PasskeyWebAuthnService {
    override fun beginRegistration(
        user: PasskeyUserIdentity,
        existingCredentialIds: List<String>,
    ): StartedPasskeyRegistration {
        val requestJson = TestObjectMapper.instance.writeValueAsString(
            mapOf(
                "userHandle" to user.userHandle,
                "username" to user.username,
                "existingCredentialIds" to existingCredentialIds,
            ),
        )
        return StartedPasskeyRegistration(
            requestJson = requestJson,
            credentialCreationJson = """
                {
                  "publicKey": {
                    "challenge": "test-registration-challenge",
                    "rp": {
                      "name": "Spring Playground Test",
                      "id": "localhost"
                    },
                    "user": {
                      "id": "${user.userHandle}",
                      "name": "${user.username}",
                      "displayName": "${user.displayName}"
                    },
                    "pubKeyCredParams": [
                      { "type": "public-key", "alg": -7 }
                    ],
                    "excludeCredentials": [
                      { "type": "public-key", "id": "existing-passkey" }
                    ]
                  }
                }
            """.trimIndent(),
        )
    }

    override fun finishRegistration(requestJson: String, credentialJson: String): FinishedPasskeyRegistration {
        val credential = TestObjectMapper.instance.readTree(credentialJson)
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
            credentialRequestJson = """
                {
                  "publicKey": {
                    "challenge": "test-login-challenge",
                    "rpId": "localhost",
                    "timeout": 300000,
                    "allowCredentials": [
                      { "type": "public-key", "id": "allowed-passkey" }
                    ],
                    "userVerification": "preferred"
                  }
                }
            """.trimIndent(),
        )

    override fun finishAuthentication(requestJson: String, credentialJson: String): FinishedPasskeyAuthentication {
        val credential = TestObjectMapper.instance.readTree(credentialJson)
        return FinishedPasskeyAuthentication(
            credentialId = credential["credentialId"].asText(),
            signatureCount = 1,
            backupEligible = true,
            backupState = true,
        )
    }
}
