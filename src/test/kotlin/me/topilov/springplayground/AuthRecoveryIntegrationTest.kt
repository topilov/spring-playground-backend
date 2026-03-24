package me.topilov.springplayground

import me.topilov.springplayground.auth.RecordingEmailService
import me.topilov.springplayground.support.SecurityIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AuthRecoveryIntegrationTest : SecurityIntegrationTestSupport() {
    @Test
    fun `register endpoint is public creates profile and sends welcome email without session`() {
        val unique = uniqueSuffix()
        val username = "new-user-$unique"
        val email = "new-user-$unique@example.com"
        val password = "very-secret-password"

        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPayload(username, email, password)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value(username))
            .andExpect(jsonPath("$.email").value(email))
            .andReturn()

        assertThat(result.response.getCookie("JSESSIONID")).isNull()
        assertThat(userExists(email)).isTrue()
        assertThat(profileExists(email)).isTrue()
        assertThat(recordingEmailService.sentEmails()).hasSize(1)
        assertThat(recordingEmailService.sentEmails().single().recipientEmail).isEqualTo(email)
        assertThat(recordingEmailService.sentEmails().single().kind)
            .isEqualTo(RecordingEmailService.SentEmail.Kind.REGISTRATION_VERIFICATION)
        assertThat(extractToken(recordingEmailService.sentEmails().single())).isNotBlank()

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload(email, password)),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
    }

    @Test
    fun `duplicate register request returns conflict`() {
        val unique = uniqueSuffix()
        val username = "duplicate-$unique"
        val email = "duplicate-$unique@example.com"
        val payload = registerPayload(username, email, "very-secret-password")

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload),
        ).andExpect(status().isConflict)
    }

    @Test
    fun `database enforces case insensitive auth identity uniqueness`() {
        assertThrows<DataIntegrityViolationException> {
            jdbcTemplate.update(
                """
                INSERT INTO auth_user (username, email, password_hash, role, enabled, created_at, updated_at)
                VALUES (?, ?, ?, 'USER', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """.trimIndent(),
                "Demo",
                "another@example.com",
                "\$2a\$10\$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuvabcd",
            )
        }

        assertThrows<DataIntegrityViolationException> {
            jdbcTemplate.update(
                """
                INSERT INTO auth_user (username, email, password_hash, role, enabled, created_at, updated_at)
                VALUES (?, ?, ?, 'USER', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """.trimIndent(),
                "another-user",
                "DEMO@EXAMPLE.COM",
                "\$2a\$10\$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuvabcd",
            )
        }
    }

    @Test
    fun `forgot password returns accepted for existing and missing email`() {
        mockMvc.perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(emailCaptchaPayload("demo@example.com")),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accepted").value(true))

        assertThat(recordingEmailService.sentEmails()).hasSize(1)
        assertThat(recordingEmailService.sentEmails().single().kind).isEqualTo(RecordingEmailService.SentEmail.Kind.RESET_PASSWORD)

        recordingEmailService.clear()

        mockMvc.perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(emailCaptchaPayload("missing@example.com")),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accepted").value(true))

        assertThat(recordingEmailService.sentEmails()).isEmpty()
    }

    @Test
    fun `forgot password cooldown stays enumeration safe while suppressing repeated sends`() {
        mockMvc.perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(emailCaptchaPayload("demo@example.com")),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accepted").value(true))

        assertThat(recordingEmailService.sentEmails()).hasSize(1)
        recordingEmailService.clear()

        mockMvc.perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(emailCaptchaPayload("demo@example.com")),
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.code").value("COOLDOWN_ACTIVE"))
            .andExpect(jsonPath("$.retryAfterSeconds").isNumber)

        assertThat(recordingEmailService.sentEmails()).isEmpty()

        mockMvc.perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(emailCaptchaPayload("missing@example.com")),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accepted").value(true))

        assertThat(recordingEmailService.sentEmails()).isEmpty()

        mockMvc.perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(emailCaptchaPayload("missing@example.com")),
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.code").value("COOLDOWN_ACTIVE"))
            .andExpect(jsonPath("$.retryAfterSeconds").isNumber)
    }

    @Test
    fun `password reset tokens are not persisted in postgres`() {
        mockMvc.perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(emailCaptchaPayload("demo@example.com")),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accepted").value(true))

        assertThat(passwordResetTokenTableExists()).isFalse()
    }

    @Test
    fun `verify email accepts valid token and enables login`() {
        val unique = uniqueSuffix()
        val username = "verify-user-$unique"
        val email = "verify-user-$unique@example.com"
        val password = "verify-password"

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPayload(username, email, password)),
        )
            .andExpect(status().isOk)

        val token = extractToken(recordingEmailService.sentEmails().single())

        mockMvc.perform(
            post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$token"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.verified").value(true))

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload(email, password)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value(username))
    }

    @Test
    fun `resend verification email rotates token and keeps missing email opaque`() {
        val unique = uniqueSuffix()
        val username = "resend-user-$unique"
        val email = "resend-user-$unique@example.com"
        val password = "resend-password"

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPayload(username, email, password)),
        )
            .andExpect(status().isOk)

        val firstToken = extractToken(recordingEmailService.sentEmails().single())
        recordingEmailService.clear()

        mockMvc.perform(
            post("/api/auth/resend-verification-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(emailCaptchaPayload(email)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accepted").value(true))

        val resentMessage = recordingEmailService.sentEmails().single()
        val secondToken = extractToken(resentMessage)
        assertThat(secondToken).isNotEqualTo(firstToken)

        mockMvc.perform(
            post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$secondToken"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.verified").value(true))

        recordingEmailService.clear()

        mockMvc.perform(
            post("/api/auth/resend-verification-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(emailCaptchaPayload("missing-$unique@example.com")),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accepted").value(true))

        assertThat(recordingEmailService.sentEmails()).isEmpty()
    }

    @Test
    fun `resend verification cooldown returns stable throttled response with retry timing`() {
        val unique = uniqueSuffix()
        val username = "cooldown-user-$unique"
        val email = "cooldown-user-$unique@example.com"

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPayload(username, email, "very-secret-password")),
        )
            .andExpect(status().isOk)

        recordingEmailService.clear()

        mockMvc.perform(
            post("/api/auth/resend-verification-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(emailCaptchaPayload(email)),
        )
            .andExpect(status().isOk)

        mockMvc.perform(
            post("/api/auth/resend-verification-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(emailCaptchaPayload(email)),
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.code").value("COOLDOWN_ACTIVE"))
            .andExpect(jsonPath("$.retryAfterSeconds").isNumber)
    }

    @Test
    fun `reset password accepts valid token and updates login password`() {
        val unique = uniqueSuffix()
        val username = "reset-user-$unique"
        val email = "reset-user-$unique@example.com"
        val oldPassword = "old-password-value"
        val newPassword = "new-password-value"

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPayload(username, email, oldPassword)),
        )
            .andExpect(status().isOk)

        val verificationToken = extractToken(recordingEmailService.sentEmails().single())
        mockMvc.perform(
            post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$verificationToken"}"""),
        )
            .andExpect(status().isOk)

        recordingEmailService.clear()

        mockMvc.perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(emailCaptchaPayload(email)),
        )
            .andExpect(status().isOk)

        val token = extractToken(recordingEmailService.sentEmails().single())

        mockMvc.perform(
            post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetPasswordPayload(token, newPassword)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reset").value(true))

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload(email, oldPassword)),
        )
            .andExpect(status().isUnauthorized)

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload(email, newPassword)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value(username))
    }

    @Test
    fun `reset password rejects invalid token`() {
        mockMvc.perform(
            post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetPasswordPayload("invalid-token", "new-password-value")),
        )
            .andExpect(status().isBadRequest)
    }
}
