package me.topilov.springplayground.auth.passkey

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PasskeyOptionsMapperTest {
    private val mapper = PasskeyOptionsMapper()

    @Test
    fun `registration options unwrap publicKey object and keep binary members as base64url strings`() {
        val json = """
            {
              "publicKey": {
                "challenge": "base64url-challenge",
                "rp": {
                  "name": "Spring Playground",
                  "id": "localhost"
                },
                "user": {
                  "id": "base64url-user-id",
                  "name": "demo",
                  "displayName": "Demo User"
                },
                "pubKeyCredParams": [
                  { "type": "public-key", "alg": -7 }
                ],
                "excludeCredentials": [
                  { "type": "public-key", "id": "base64url-existing-credential" }
                ]
              }
            }
        """.trimIndent()

        val result = mapper.registrationOptionsFromCredentialsCreateJson(json)

        assertThat(result.challenge).isEqualTo("base64url-challenge")
        assertThat(result.user.id).isEqualTo("base64url-user-id")
        assertThat(result.excludeCredentials).hasSize(1)
        assertThat(result.excludeCredentials.first().id).isEqualTo("base64url-existing-credential")
    }

    @Test
    fun `login options unwrap publicKey object and keep allowCredentials ids as base64url strings`() {
        val json = """
            {
              "publicKey": {
                "challenge": "base64url-login-challenge",
                "rpId": "localhost",
                "timeout": 300000,
                "allowCredentials": [
                  { "type": "public-key", "id": "base64url-allowed-credential" }
                ],
                "userVerification": "preferred"
              }
            }
        """.trimIndent()

        val result = mapper.authenticationOptionsFromCredentialsGetJson(json)

        assertThat(result.challenge).isEqualTo("base64url-login-challenge")
        assertThat(result.allowCredentials).hasSize(1)
        assertThat(result.allowCredentials.first().id).isEqualTo("base64url-allowed-credential")
    }
}
