package me.topilov.springplayground

import me.topilov.springplayground.auth.RecordingEmailService
import me.topilov.springplayground.support.SecurityIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.net.URI

class ProfileAccountIntegrationTest : SecurityIntegrationTestSupport() {
    @Test
    fun `authenticated user can change username and use it for subsequent login`() {
        val session = loginSession("demo", "demo-password")
        val newUsername = "renamed-demo-${uniqueSuffix()}"

        mockMvc.perform(
            post("/api/profile/me/username")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"  $newUsername  "}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value(newUsername))

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload("demo", "demo-password")),
        )
            .andExpect(status().isUnauthorized)

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload(newUsername, "demo-password")),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value(newUsername))
    }

    @Test
    fun `change username rejects already used value`() {
        val unique = uniqueSuffix()
        val username = "duplicate-target-$unique"
        val email = "duplicate-target-$unique@example.com"

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPayload(username, email, "very-secret-password")),
        )
            .andExpect(status().isOk)

        val session = loginSession("demo", "demo-password")

        mockMvc.perform(
            post("/api/profile/me/username")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"$username"}"""),
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `authenticated user can change password with current password`() {
        val session = loginSession("demo", "demo-password")
        val newPassword = "updated-demo-password"

        mockMvc.perform(
            post("/api/profile/me/password")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"demo-password","newPassword":"$newPassword"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.changed").value(true))

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload("demo", "demo-password")),
        )
            .andExpect(status().isUnauthorized)

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload("demo", newPassword)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authenticated").value(true))
    }

    @Test
    fun `change password rejects incorrect current password`() {
        val session = loginSession("demo", "demo-password")

        mockMvc.perform(
            post("/api/profile/me/password")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"wrong-password","newPassword":"updated-demo-password"}"""),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `email change request verifies new email invalidates superseded token and updates login identifiers`() {
        val session = loginSession("demo", "demo-password")
        val firstEmail = "demo-renamed-${uniqueSuffix()}@example.com"
        val secondEmail = "demo-renamed-${uniqueSuffix()}@example.com"

        mockMvc.perform(
            post("/api/profile/me/email/change-request")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"newEmail":"${firstEmail.uppercase()}"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accepted").value(true))

        assertThat(recordingEmailService.sentEmails()).hasSize(1)
        assertThat(recordingEmailService.sentEmails().single().kind)
            .isEqualTo(RecordingEmailService.SentEmail.Kind.EMAIL_CHANGE_VERIFICATION)
        assertThat(recordingEmailService.sentEmails().single().recipientEmail).isEqualTo(firstEmail.lowercase())
        assertThat(URI.create(recordingEmailService.sentEmails().single().url).path).isEqualTo("/verify-email-change")
        val firstToken = extractToken(recordingEmailService.sentEmails().single())
        recordingEmailService.clear()

        mockMvc.perform(
            post("/api/profile/me/email/change-request")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"newEmail":"$secondEmail"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accepted").value(true))

        assertThat(recordingEmailService.sentEmails()).hasSize(1)
        assertThat(recordingEmailService.sentEmails().single().recipientEmail).isEqualTo(secondEmail)
        assertThat(URI.create(recordingEmailService.sentEmails().single().url).path).isEqualTo("/verify-email-change")
        val secondToken = extractToken(recordingEmailService.sentEmails().single())
        assertThat(secondToken).isNotEqualTo(firstToken)

        mockMvc.perform(
            post("/api/profile/me/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(emailChangeVerifyPayload(firstToken)),
        )
            .andExpect(status().isBadRequest)

        mockMvc.perform(
            post("/api/profile/me/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(emailChangeVerifyPayload(secondToken)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value(secondEmail))

        mockMvc.perform(
            post("/api/profile/me/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(emailChangeVerifyPayload(secondToken)),
        )
            .andExpect(status().isBadRequest)

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload("demo@example.com", "demo-password")),
        )
            .andExpect(status().isUnauthorized)

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload(secondEmail, "demo-password")),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value(secondEmail))
    }

    @Test
    fun `email change request rejects already used email`() {
        val unique = uniqueSuffix()
        val username = "email-duplicate-$unique"
        val email = "email-duplicate-$unique@example.com"

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPayload(username, email, "very-secret-password")),
        )
            .andExpect(status().isOk)

        val session = loginSession("demo", "demo-password")

        mockMvc.perform(
            post("/api/profile/me/email/change-request")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"newEmail":"${email.uppercase()}"}"""),
        )
            .andExpect(status().isConflict)
    }
}
