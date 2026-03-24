package me.topilov.springplayground

import me.topilov.springplayground.support.SecurityIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class PasskeyIntegrationTest : SecurityIntegrationTestSupport() {
    @Test
    fun `authenticated user can register list rename and delete passkeys`() {
        val session = loginSession("demo", "demo-password")

        val optionsResult = mockMvc.perform(
            post("/api/auth/passkeys/register/options")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nickname":"MacBook Touch ID"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ceremonyId").isString)
            .andExpect(jsonPath("$.publicKey.challenge").isString)
            .andExpect(jsonPath("$.publicKey.publicKey").doesNotExist())
            .andExpect(jsonPath("$.publicKey.user.id").isString)
            .andExpect(jsonPath("$.publicKey.excludeCredentials[0].id").isString)
            .andReturn()

        val ceremonyId = jsonField(optionsResult, "ceremonyId")

        mockMvc.perform(
            post("/api/auth/passkeys/register/verify")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "ceremonyId":"$ceremonyId",
                      "credential":{
                        "credentialId":"demo-passkey-1",
                        "label":"MacBook Touch ID"
                      }
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("MacBook Touch ID"))
            .andExpect(jsonPath("$.createdAt").isString)
            .andExpect(jsonPath("$.lastUsedAt").doesNotExist())

        val listResult = mockMvc.perform(get("/api/auth/passkeys").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("MacBook Touch ID"))
            .andReturn()

        val passkeyId = jsonField(listResult, "0.id")

        mockMvc.perform(
            patch("/api/auth/passkeys/$passkeyId")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Work Laptop"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Work Laptop"))

        mockMvc.perform(delete("/api/auth/passkeys/$passkeyId").session(session))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/auth/passkeys").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)
    }

    @Test
    fun `passkey login creates same authenticated session shape as password login`() {
        val authenticatedSession = loginSession("demo", "demo-password")

        val registrationOptions = mockMvc.perform(
            post("/api/auth/passkeys/register/options")
                .session(authenticatedSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nickname":"Demo Login Passkey"}"""),
        )
            .andExpect(status().isOk)
            .andReturn()

        val registrationCeremonyId = jsonField(registrationOptions, "ceremonyId")

        mockMvc.perform(
            post("/api/auth/passkeys/register/verify")
                .session(authenticatedSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "ceremonyId":"$registrationCeremonyId",
                      "credential":{
                        "credentialId":"demo-login-passkey",
                        "label":"Demo Login Passkey"
                      }
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)

        val optionsResult = mockMvc.perform(
            post("/api/auth/passkey-login/options")
                .contentType(MediaType.APPLICATION_JSON)
                .content(passkeyLoginOptionsPayload()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ceremonyId").isString)
            .andExpect(jsonPath("$.publicKey.challenge").isString)
            .andExpect(jsonPath("$.publicKey.publicKey").doesNotExist())
            .andExpect(jsonPath("$.publicKey.allowCredentials[0].id").isString)
            .andReturn()

        val ceremonyId = jsonField(optionsResult, "ceremonyId")

        val verifyResult = mockMvc.perform(
            post("/api/auth/passkey-login/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "ceremonyId":"$ceremonyId",
                      "captchaToken":"test-captcha-token",
                      "credential":{
                        "credentialId":"demo-login-passkey"
                      }
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.userId").value(1))
            .andExpect(jsonPath("$.username").value("demo"))
            .andExpect(jsonPath("$.email").value("demo@example.com"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andReturn()

        assertThat(verifyResult.request.session).isInstanceOf(MockHttpSession::class.java)
    }

    @Test
    fun `passkey ceremony cannot be reused after successful verify`() {
        val session = loginSession("demo", "demo-password")

        val registrationOptions = mockMvc.perform(
            post("/api/auth/passkeys/register/options")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nickname":"Reusable Passkey"}"""),
        )
            .andExpect(status().isOk)
            .andReturn()

        val ceremonyId = jsonField(registrationOptions, "ceremonyId")
        val payload = """
            {
              "ceremonyId":"$ceremonyId",
              "credential":{
                "credentialId":"single-use-passkey",
                "label":"Reusable Passkey"
              }
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/auth/passkeys/register/verify")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload),
        )
            .andExpect(status().isOk)

        mockMvc.perform(
            post("/api/auth/passkeys/register/verify")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `registration verify rejects ceremony from a different authenticated user`() {
        val demoSession = loginSession("demo", "demo-password")
        val otherSession = createVerifiedUserAndLogin()

        val optionsResult = mockMvc.perform(
            post("/api/auth/passkeys/register/options")
                .session(demoSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nickname":"Bound Passkey"}"""),
        )
            .andExpect(status().isOk)
            .andReturn()

        val ceremonyId = jsonField(optionsResult, "ceremonyId")

        mockMvc.perform(
            post("/api/auth/passkeys/register/verify")
                .session(otherSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "ceremonyId":"$ceremonyId",
                      "credential":{
                        "credentialId":"wrong-user-passkey"
                      }
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `user cannot rename or delete another users passkey`() {
        val demoSession = loginSession("demo", "demo-password")
        val optionsResult = mockMvc.perform(
            post("/api/auth/passkeys/register/options")
                .session(demoSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nickname":"Protected Passkey"}"""),
        )
            .andExpect(status().isOk)
            .andReturn()

        val ceremonyId = jsonField(optionsResult, "ceremonyId")

        mockMvc.perform(
            post("/api/auth/passkeys/register/verify")
                .session(demoSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "ceremonyId":"$ceremonyId",
                      "credential":{
                        "credentialId":"protected-passkey"
                      }
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)

        val passkeyId = jsonField(
            mockMvc.perform(get("/api/auth/passkeys").session(demoSession))
                .andExpect(status().isOk)
                .andReturn(),
            "0.id",
        )
        val otherSession = createVerifiedUserAndLogin()

        mockMvc.perform(
            patch("/api/auth/passkeys/$passkeyId")
                .session(otherSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Stolen"}"""),
        )
            .andExpect(status().isNotFound)

        mockMvc.perform(delete("/api/auth/passkeys/$passkeyId").session(otherSession))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `duplicate passkey credential registration returns conflict`() {
        val firstSession = loginSession("demo", "demo-password")
        val secondSession = createVerifiedUserAndLogin()

        val firstCeremonyId = jsonField(
            mockMvc.perform(
                post("/api/auth/passkeys/register/options")
                    .session(firstSession)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nickname":"Primary"}"""),
            )
                .andExpect(status().isOk)
                .andReturn(),
            "ceremonyId",
        )

        mockMvc.perform(
            post("/api/auth/passkeys/register/verify")
                .session(firstSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "ceremonyId":"$firstCeremonyId",
                      "credential":{
                        "credentialId":"duplicate-passkey"
                      }
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)

        val secondCeremonyId = jsonField(
            mockMvc.perform(
                post("/api/auth/passkeys/register/options")
                    .session(secondSession)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nickname":"Secondary"}"""),
            )
                .andExpect(status().isOk)
                .andReturn(),
            "ceremonyId",
        )

        mockMvc.perform(
            post("/api/auth/passkeys/register/verify")
                .session(secondSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "ceremonyId":"$secondCeremonyId",
                      "credential":{
                        "credentialId":"duplicate-passkey"
                      }
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isConflict)
    }
}
