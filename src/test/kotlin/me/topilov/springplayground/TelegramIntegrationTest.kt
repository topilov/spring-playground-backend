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
    fun `user can create list update and delete telegram modes`() {
        val session = loginSession("demo", "demo-password")
        clearTelegramModes()

        mockMvc.perform(
            post("/api/profile/me/telegram/modes")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mode":"work","emojiStatusDocumentId":"1001"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.mode").value("work"))
            .andExpect(jsonPath("$.emojiStatusDocumentId").value("1001"))

        mockMvc.perform(get("/api/profile/me/telegram/modes").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.modes[0].mode").value("work"))
            .andExpect(jsonPath("$.modes[0].emojiStatusDocumentId").value("1001"))

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/profile/me/telegram/modes/work")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"newMode":"deep_work","emojiStatusDocumentId":"1002"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mode").value("deep_work"))
            .andExpect(jsonPath("$.emojiStatusDocumentId").value("1002"))

        mockMvc.perform(
            delete("/api/profile/me/telegram/modes/deep_work")
                .session(session),
        )
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/profile/me/telegram/modes").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.modes").isEmpty)
    }

    @Test
    fun `telegram settings snapshot exposes active focus mode and user-defined modes`() {
        val session = loginSession("demo", "demo-password")

        mockMvc.perform(
            put("/api/profile/me/telegram/focus-settings")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"defaultEmojiStatusDocumentId":"7000"}"""),
        )
            .andExpect(status().isOk)

        mockMvc.perform(get("/api/profile/me/telegram").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.defaultEmojiStatusDocumentId").value("7000"))
            .andExpect(jsonPath("$.activeFocusMode").doesNotExist())
            .andExpect(jsonPath("$.modes").isArray)
            .andExpect(jsonPath("$.resolvedEmojiMappings").doesNotExist())
            .andExpect(jsonPath("$.activeFocusModes").doesNotExist())
    }

    @Test
    fun `automation updates resolve user-defined string modes`() {
        val session = loginSession("demo", "demo-password")
        clearTelegramModes()
        connectTelegram(session)
        val token = createAutomationToken(session)

        mockMvc.perform(
            post("/api/profile/me/telegram/modes")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mode":"work","emojiStatusDocumentId":"1001"}"""),
        )
            .andExpect(status().isCreated)

        mockMvc.perform(
            put("/api/profile/me/telegram/focus-settings")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"defaultEmojiStatusDocumentId":"7000"}"""),
        )
            .andExpect(status().isOk)

        mockMvc.perform(
            post("/api/telegram/focus-updates")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mode":"work","active":true}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.applied").value(true))
            .andExpect(jsonPath("$.activeFocusMode").value("work"))
            .andExpect(jsonPath("$.appliedEmojiStatusDocumentId").value("1001"))
    }

    @Test
    fun `focus update endpoint recalculates effective mode and restores explicit no focus status`() {
        val session = loginSession("demo", "demo-password")
        clearTelegramModes()
        connectTelegram(session)
        val token = createAutomationToken(session)

        createMode(session, "personal", "1001")
        createMode(session, "airplane", "1002")
        createMode(session, "do_not_disturb", "1003")
        createMode(session, "reduce_interruptions", "1004")
        createMode(session, "sleep", "1005")

        mockMvc.perform(
            put("/api/profile/me/telegram/focus-settings")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"defaultEmojiStatusDocumentId":"7000"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.defaultEmojiStatusDocumentId").value("7000"))
            .andExpect(jsonPath("$.modes").isArray)

        mockMvc.perform(
            post("/api/telegram/focus-updates")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mode":"personal","active":true}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.applied").value(true))
            .andExpect(jsonPath("$.activeFocusMode").value("personal"))
            .andExpect(jsonPath("$.appliedEmojiStatusDocumentId").value("1001"))

        assertThat(fakeTelegramClientGateway.currentEmojiStatusDocumentIdForUser(1L)).isEqualTo("1001")

        mockMvc.perform(
            post("/api/telegram/focus-updates")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mode":"sleep","active":true}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.activeFocusMode").value("sleep"))
            .andExpect(jsonPath("$.appliedEmojiStatusDocumentId").value("1005"))

        assertThat(fakeTelegramClientGateway.currentEmojiStatusDocumentIdForUser(1L)).isEqualTo("1005")

        mockMvc.perform(
            post("/api/telegram/focus-updates")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mode":"sleep","active":false}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.activeFocusMode").doesNotExist())
            .andExpect(jsonPath("$.appliedEmojiStatusDocumentId").value("7000"))

        mockMvc.perform(
            post("/api/telegram/focus-updates")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mode":"personal","active":false}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.activeFocusMode").doesNotExist())
            .andExpect(jsonPath("$.appliedEmojiStatusDocumentId").value("7000"))

        assertThat(fakeTelegramClientGateway.currentEmojiStatusDocumentIdForUser(1L)).isEqualTo("7000")
    }

    @Test
    fun `focus update endpoint switches directly between personal and do not disturb without falling back to default`() {
        val session = loginSession("demo", "demo-password")
        clearTelegramModes()
        connectTelegram(session)
        val token = createAutomationToken(session)

        createMode(session, "personal", "1001")
        createMode(session, "do_not_disturb", "1003")

        mockMvc.perform(
            put("/api/profile/me/telegram/focus-settings")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"defaultEmojiStatusDocumentId":"7000"}"""),
        )
            .andExpect(status().isOk)

        mockMvc.perform(
            post("/api/telegram/focus-updates")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mode":"personal","active":true}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.activeFocusMode").value("personal"))
            .andExpect(jsonPath("$.appliedEmojiStatusDocumentId").value("1001"))

        assertThat(fakeTelegramClientGateway.currentEmojiStatusDocumentIdForUser(1L)).isEqualTo("1001")

        mockMvc.perform(
            post("/api/telegram/focus-updates")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mode":"do_not_disturb","active":true}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.activeFocusMode").value("do_not_disturb"))
            .andExpect(jsonPath("$.appliedEmojiStatusDocumentId").value("1003"))

        assertThat(fakeTelegramClientGateway.currentEmojiStatusDocumentIdForUser(1L)).isEqualTo("1003")

        mockMvc.perform(
            post("/api/telegram/focus-updates")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mode":"personal","active":true}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.activeFocusMode").value("personal"))
            .andExpect(jsonPath("$.appliedEmojiStatusDocumentId").value("1001"))

        assertThat(fakeTelegramClientGateway.currentEmojiStatusDocumentIdForUser(1L)).isEqualTo("1001")
    }

    @Test
    fun `disconnect preserves focus preferences`() {
        val session = loginSession("demo", "demo-password")
        clearTelegramModes()
        connectTelegram(session)

        createMode(session, "sleep", "1005")

        mockMvc.perform(
            put("/api/profile/me/telegram/focus-settings")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"defaultEmojiStatusDocumentId":"7000"}"""),
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
            .andExpect(jsonPath("$.modes[0].mode").value("sleep"))
            .andExpect(jsonPath("$.modes[0].emojiStatusDocumentId").value("1005"))
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

    private fun clearTelegramModes() {
        jdbcTemplate.update("UPDATE telegram_account_connection SET active_mode_id = NULL WHERE user_id = ?", 1L)
        jdbcTemplate.update("DELETE FROM telegram_mode WHERE user_id = ?", 1L)
    }

    private fun createMode(
        session: org.springframework.mock.web.MockHttpSession,
        mode: String,
        emojiStatusDocumentId: String,
    ) {
        mockMvc.perform(
            post("/api/profile/me/telegram/modes")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mode":"$mode","emojiStatusDocumentId":"$emojiStatusDocumentId"}"""),
        )
            .andExpect(status().isCreated)
    }
}
