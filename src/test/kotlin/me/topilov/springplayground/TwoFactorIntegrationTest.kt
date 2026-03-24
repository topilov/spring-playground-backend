package me.topilov.springplayground

import me.topilov.springplayground.support.SecurityIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class TwoFactorIntegrationTest : SecurityIntegrationTestSupport() {
    @Test
    fun `authenticated user can enable inspect regenerate backup codes and disable totp`() {
        val session = loginSession("demo", "demo-password")

        mockMvc.perform(get("/api/auth/2fa/status").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.pendingSetup").value(false))
            .andExpect(jsonPath("$.backupCodesRemaining").value(0))

        val setupResult = mockMvc.perform(post("/api/auth/2fa/setup/start").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.secret").isString)
            .andExpect(jsonPath("$.otpauthUri").value(org.hamcrest.Matchers.containsString("otpauth://totp/")))
            .andReturn()

        val secret = jsonField(setupResult, "secret")
        val setupCode = currentTotpCode(secret)

        val confirmResult = mockMvc.perform(
            post("/api/auth/2fa/setup/confirm")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"$setupCode"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.backupCodes.length()").value(10))
            .andReturn()

        val firstBackupCode = jsonField(confirmResult, "backupCodes.0")

        mockMvc.perform(get("/api/auth/2fa/status").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.pendingSetup").value(false))
            .andExpect(jsonPath("$.backupCodesRemaining").value(10))
            .andExpect(jsonPath("$.enabledAt").isString)

        assertThat(totpCredentialExistsForUser(1)).isTrue()
        assertThat(activeBackupCodeCountForUser(1)).isEqualTo(10)
        assertThat(storedBackupCodeHashesForUser(1)).doesNotContain(firstBackupCode)

        val regenerateResult = mockMvc.perform(post("/api/auth/2fa/backup-codes/regenerate").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.backupCodes.length()").value(10))
            .andReturn()

        val regeneratedBackupCode = jsonField(regenerateResult, "backupCodes.0")
        assertThat(regeneratedBackupCode).isNotEqualTo(firstBackupCode)

        mockMvc.perform(get("/api/auth/2fa/status").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.backupCodesRemaining").value(10))

        mockMvc.perform(post("/api/auth/2fa/disable").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.disabled").value(true))

        mockMvc.perform(get("/api/auth/2fa/status").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.pendingSetup").value(false))
            .andExpect(jsonPath("$.backupCodesRemaining").value(0))

        assertThat(totpCredentialExistsForUser(1)).isFalse()
        assertThat(activeBackupCodeCountForUser(1)).isEqualTo(0)
    }

    @Test
    fun `password login returns short lived challenge and totp verification creates the normal session`() {
        val session = loginSession("demo", "demo-password")
        val secret = jsonField(
            mockMvc.perform(post("/api/auth/2fa/setup/start").session(session))
                .andExpect(status().isOk)
                .andReturn(),
            "secret",
        )

        mockMvc.perform(
            post("/api/auth/2fa/setup/confirm")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"${currentTotpCode(secret)}"}"""),
        )
            .andExpect(status().isOk)

        mockMvc.perform(post("/api/auth/logout").session(session))
            .andExpect(status().isNoContent)

        val loginResult = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload("demo", "demo-password")),
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.requiresTwoFactor").value(true))
            .andExpect(jsonPath("$.loginChallengeId").isString)
            .andExpect(jsonPath("$.methods[0]").value("TOTP"))
            .andExpect(jsonPath("$.methods[1]").value("BACKUP_CODE"))
            .andReturn()

        assertThat(loginResult.response.getCookie("JSESSIONID")).isNull()

        val challengeId = jsonField(loginResult, "loginChallengeId")

        val verifyResult = mockMvc.perform(
            post("/api/auth/2fa/login/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(twoFactorPayload(challengeId, currentTotpCode(secret))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.username").value("demo"))
            .andExpect(jsonPath("$.email").value("demo@example.com"))
            .andReturn()

        assertThat(verifyResult.request.session).isInstanceOf(MockHttpSession::class.java)
        assertThat(inMemoryTwoFactorLoginChallengeStore.find(challengeId)).isNull()

        mockMvc.perform(
            post("/api/auth/2fa/login/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(twoFactorPayload(challengeId, currentTotpCode(secret))),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `login rejects missing captcha token with stable bad request response`() {
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"usernameOrEmail":"demo","password":"demo-password"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("CAPTCHA_VALIDATION_FAILED"))
            .andExpect(jsonPath("$.error").value("Captcha verification failed. Please try again."))
    }

    @Test
    fun `login returns expired captcha message for timeout or duplicate token`() {
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"usernameOrEmail":"demo","password":"demo-password","captchaToken":"duplicate-captcha-token"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("CAPTCHA_VALIDATION_FAILED"))
            .andExpect(jsonPath("$.error").value("Captcha expired. Please try again."))
    }

    @Test
    fun `login returns temporarily unavailable captcha message for internal verification errors`() {
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"usernameOrEmail":"demo","password":"demo-password","captchaToken":"internal-error-captcha-token"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("CAPTCHA_VALIDATION_FAILED"))
            .andExpect(jsonPath("$.error").value("Captcha verification is temporarily unavailable. Please try again."))
    }

    @Test
    fun `login challenge is invalidated after a failed second factor attempt`() {
        val backupCodes = enableTotpForDemo()

        val challengeId = jsonField(
            mockMvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginPayload("demo", "demo-password")),
            )
                .andExpect(status().isAccepted)
                .andReturn(),
            "loginChallengeId",
        )

        mockMvc.perform(
            post("/api/auth/2fa/login/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(twoFactorPayload(challengeId, "000000")),
        )
            .andExpect(status().isUnauthorized)

        mockMvc.perform(
            post("/api/auth/2fa/login/verify-backup-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(twoFactorBackupPayload(challengeId, backupCodes.first())),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `backup code login is one time use and stored only as hashes`() {
        val backupCodes = enableTotpForDemo()
        val firstBackupCode = backupCodes.first()
        assertThat(storedBackupCodeHashesForUser(1)).doesNotContain(firstBackupCode)

        val firstChallengeId = jsonField(
            mockMvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginPayload("demo", "demo-password")),
            )
                .andExpect(status().isAccepted)
                .andReturn(),
            "loginChallengeId",
        )

        mockMvc.perform(
            post("/api/auth/2fa/login/verify-backup-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(twoFactorBackupPayload(firstChallengeId, firstBackupCode)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authenticated").value(true))

        val secondChallengeId = jsonField(
            mockMvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginPayload("demo", "demo-password")),
            )
                .andExpect(status().isAccepted)
                .andReturn(),
            "loginChallengeId",
        )

        mockMvc.perform(
            post("/api/auth/2fa/login/verify-backup-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(twoFactorBackupPayload(secondChallengeId, firstBackupCode)),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `login and two factor verification are throttled independently`() {
        val backupCodes = enableTotpForDemo()
        val challengeId = jsonField(
            mockMvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginPayload("demo", "demo-password")),
            )
                .andExpect(status().isAccepted)
                .andReturn(),
            "loginChallengeId",
        )

        repeat(5) {
            mockMvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginPayload("demo", "wrong-password")),
            )
                .andExpect(status().isUnauthorized)
        }

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload("demo", "wrong-password")),
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.code").value("LOGIN_THROTTLED"))
            .andExpect(jsonPath("$.retryAfterSeconds").isNumber)

        mockMvc.perform(
            post("/api/auth/2fa/login/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(twoFactorPayload(challengeId, "000000")),
        )
            .andExpect(status().isUnauthorized)

        mockMvc.perform(
            post("/api/auth/2fa/login/verify-backup-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(twoFactorBackupPayload(challengeId, backupCodes.first())),
        )
            .andExpect(status().isBadRequest)
    }
}
