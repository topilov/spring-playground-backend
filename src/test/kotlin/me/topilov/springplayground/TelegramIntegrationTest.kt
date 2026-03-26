package me.topilov.springplayground

import me.topilov.springplayground.support.SecurityIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class TelegramIntegrationTest : SecurityIntegrationTestSupport() {
    @Test
    fun `telegram settings endpoint requires authentication`() {
        mockMvc.perform(get("/api/profile/me/telegram"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `authenticated user can connect telegram account through code and password steps`() {
        val session = loginSession("demo", "demo-password")

        val startResult = mockMvc.perform(
            post("/api/profile/me/telegram/connect/start")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"phoneNumber":"+15551234567"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pendingAuthId").isString)
            .andExpect(jsonPath("$.nextStep").value("CODE"))
            .andReturn()

        val pendingAuthId = jsonField(startResult, "pendingAuthId")

        mockMvc.perform(
            post("/api/profile/me/telegram/connect/confirm-code")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"pendingAuthId":"$pendingAuthId","code":"22222"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.connected").value(false))
            .andExpect(jsonPath("$.nextStep").value("PASSWORD"))

        mockMvc.perform(
            post("/api/profile/me/telegram/connect/confirm-password")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"pendingAuthId":"$pendingAuthId","password":"telegram-secret"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.connected").value(true))
            .andExpect(jsonPath("$.connectionStatus").value("CONNECTED"))
            .andExpect(jsonPath("$.telegramUser.phoneNumber").value("+15551234567"))
            .andExpect(jsonPath("$.telegramUser.premium").value(true))

        mockMvc.perform(get("/api/profile/me/telegram").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.connected").value(true))
            .andExpect(jsonPath("$.connectionStatus").value("CONNECTED"))
            .andExpect(jsonPath("$.telegramUser.phoneNumber").value("+15551234567"))
    }

    @Test
    fun `connected user can create regenerate and revoke automation token while storing only the hash`() {
        val session = loginSession("demo", "demo-password")
        connectTelegram(session)

        val createdResult = mockMvc.perform(
            post("/api/profile/me/telegram/automation-token")
                .session(session),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").isString)
            .andExpect(jsonPath("$.tokenHint").isString)
            .andReturn()

        val createdToken = jsonField(createdResult, "token")
        assertThat(createdToken).startsWith("tgf_")

        val storedCreatedHash = jdbcTemplate.queryForObject(
            "SELECT token_hash FROM telegram_automation_token WHERE user_id = ? AND revoked_at IS NULL",
            String::class.java,
            1L,
        )
        assertThat(storedCreatedHash).isNotNull()
        assertThat(storedCreatedHash).isNotEqualTo(createdToken)
        assertThat(storedCreatedHash).hasSize(64)

        val regeneratedResult = mockMvc.perform(
            post("/api/profile/me/telegram/automation-token/regenerate")
                .session(session),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").isString)
            .andReturn()

        val regeneratedToken = jsonField(regeneratedResult, "token")
        assertThat(regeneratedToken).isNotEqualTo(createdToken)

        val storedRegeneratedHash = jdbcTemplate.queryForObject(
            "SELECT token_hash FROM telegram_automation_token WHERE user_id = ? AND revoked_at IS NULL",
            String::class.java,
            1L,
        )
        assertThat(storedRegeneratedHash).isNotEqualTo(storedCreatedHash)

        mockMvc.perform(
            delete("/api/profile/me/telegram/automation-token")
                .session(session),
        )
            .andExpect(status().isNoContent)

        mockMvc.perform(
            post("/api/telegram/focus-updates")
                .header("Authorization", "Bearer $regeneratedToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mode":"sleep","active":true}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("TELEGRAM_AUTOMATION_TOKEN_INVALID"))
    }

    @Test
    fun `focus update endpoint recalculates effective mode and restores explicit no focus status`() {
        val session = loginSession("demo", "demo-password")
        connectTelegram(session)
        val token = createAutomationToken(session)

        mockMvc.perform(
            put("/api/profile/me/telegram/focus-settings")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "defaultEmojiStatusDocumentId":"7000",
                      "mappings":{
                        "personal":"1001",
                        "airplane":"1002",
                        "do_not_disturb":"1003",
                        "reduce_interruptions":"1004",
                        "sleep":"1005"
                      }
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.defaultEmojiStatusDocumentId").value("7000"))
            .andExpect(jsonPath("$.resolvedEmojiMappings.sleep").value("1005"))

        mockMvc.perform(
            post("/api/telegram/focus-updates")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mode":"personal","active":true}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.applied").value(true))
            .andExpect(jsonPath("$.effectiveFocusMode").value("personal"))
            .andExpect(jsonPath("$.appliedEmojiStatusDocumentId").value("1001"))

        assertThat(fakeTelegramClientGateway.currentEmojiStatusDocumentIdForUser(1L)).isEqualTo("1001")

        mockMvc.perform(
            post("/api/telegram/focus-updates")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mode":"sleep","active":true}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.effectiveFocusMode").value("sleep"))
            .andExpect(jsonPath("$.appliedEmojiStatusDocumentId").value("1005"))

        assertThat(fakeTelegramClientGateway.currentEmojiStatusDocumentIdForUser(1L)).isEqualTo("1005")

        mockMvc.perform(
            post("/api/telegram/focus-updates")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mode":"sleep","active":false}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.effectiveFocusMode").value("personal"))
            .andExpect(jsonPath("$.appliedEmojiStatusDocumentId").value("1001"))

        mockMvc.perform(
            post("/api/telegram/focus-updates")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mode":"personal","active":false}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.effectiveFocusMode").doesNotExist())
            .andExpect(jsonPath("$.appliedEmojiStatusDocumentId").value("7000"))

        assertThat(fakeTelegramClientGateway.currentEmojiStatusDocumentIdForUser(1L)).isEqualTo("7000")
    }

    @Test
    fun `disconnect preserves focus preferences`() {
        val session = loginSession("demo", "demo-password")
        connectTelegram(session)

        mockMvc.perform(
            put("/api/profile/me/telegram/focus-settings")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "defaultEmojiStatusDocumentId":"7000",
                      "mappings":{
                        "sleep":"1005"
                      }
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)

        mockMvc.perform(
            delete("/api/profile/me/telegram/connect")
                .session(session),
        )
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/profile/me/telegram").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.connected").value(false))
            .andExpect(jsonPath("$.defaultEmojiStatusDocumentId").value("7000"))
            .andExpect(jsonPath("$.resolvedEmojiMappings.sleep").value("1005"))
    }

    @Test
    fun `reconnect captures the current telegram emoji status as the new explicit no focus default`() {
        val session = loginSession("demo", "demo-password")
        connectTelegram(session)

        mockMvc.perform(
            put("/api/profile/me/telegram/focus-settings")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "defaultEmojiStatusDocumentId":"7000"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)

        fakeTelegramClientGateway.setCurrentEmojiStatusDocumentIdForUser(1L, "8111")

        mockMvc.perform(
            delete("/api/profile/me/telegram/connect")
                .session(session),
        )
            .andExpect(status().isNoContent)

        connectTelegram(session)

        mockMvc.perform(get("/api/profile/me/telegram").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.connected").value(true))
            .andExpect(jsonPath("$.defaultEmojiStatusDocumentId").value("8111"))
    }

    private fun connectTelegram(session: org.springframework.mock.web.MockHttpSession) {
        val startResult = mockMvc.perform(
            post("/api/profile/me/telegram/connect/start")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"phoneNumber":"+15551234567"}"""),
        )
            .andExpect(status().isOk)
            .andReturn()

        val pendingAuthId = jsonField(startResult, "pendingAuthId")

        mockMvc.perform(
            post("/api/profile/me/telegram/connect/confirm-code")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"pendingAuthId":"$pendingAuthId","code":"11111"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.connected").value(true))
    }

    private fun createAutomationToken(session: org.springframework.mock.web.MockHttpSession): String =
        jsonField(
            mockMvc.perform(
                post("/api/profile/me/telegram/automation-token")
                    .session(session),
            )
                .andExpect(status().isOk)
                .andReturn(),
            "token",
        )
}
