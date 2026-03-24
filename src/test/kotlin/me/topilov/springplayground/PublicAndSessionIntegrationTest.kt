package me.topilov.springplayground

import me.topilov.springplayground.support.SecurityIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class PublicAndSessionIntegrationTest : SecurityIntegrationTestSupport() {
    @Test
    fun `health endpoint is public`() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `public ping endpoint is public`() {
        mockMvc.perform(get("/api/public/ping"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ok"))
    }

    @Test
    fun `openapi endpoint is public and exposes current api paths`() {
        val result = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.openapi").exists())
            .andExpect(jsonPath("$.paths['/api/public/ping']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/2fa/status']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/2fa/setup/start']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/2fa/setup/confirm']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/2fa/backup-codes/regenerate']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/2fa/disable']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/2fa/login/verify']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/2fa/login/verify-backup-code']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/logout']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/passkeys']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/passkeys/register/options']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/passkeys/register/verify']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/passkey-login/options']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/passkey-login/verify']").exists())
            .andExpect(jsonPath("$.paths['/api/profile/me']").exists())
            .andExpect(jsonPath("$.paths['/api/profile/me/username']").exists())
            .andExpect(jsonPath("$.paths['/api/profile/me/password']").exists())
            .andExpect(jsonPath("$.paths['/api/profile/me/email/change-request']").exists())
            .andExpect(jsonPath("$.paths['/api/profile/me/email/verify']").exists())
            .andExpect(jsonPath("$.paths['/api/public/ping'].get.responses['400']").doesNotExist())
            .andExpect(jsonPath("$.paths['/api/public/ping'].get.responses['409']").doesNotExist())
            .andExpect(jsonPath("$.paths['/api/profile/me'].get.responses['400']").doesNotExist())
            .andExpect(jsonPath("$.paths['/api/profile/me'].get.responses['409']").doesNotExist())
            .andReturn()

        assertThat(result.response.contentAsString).doesNotContain("http://localhost:8080")
    }

    @Test
    fun `openapi yaml endpoint is public`() {
        mockMvc.perform(get("/v3/api-docs.yaml"))
            .andExpect(status().isOk)
    }

    @Test
    fun `profile endpoint requires authentication`() {
        mockMvc.perform(get("/api/profile/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `login by username creates authenticated session`() {
        val result = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload("demo", "demo-password")),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.username").value("demo"))
            .andReturn()

        assertThat(result.request.session).isInstanceOf(MockHttpSession::class.java)
    }

    @Test
    fun `login preflight allows frontend origin`() {
        val frontendOrigin = corsProperties.allowedOrigins.first()
        mockMvc.perform(
            options("/api/auth/login")
                .header("Origin", frontendOrigin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"),
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Access-Control-Allow-Origin", frontendOrigin))
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
    }

    @Test
    fun `login response includes cors headers for frontend origin`() {
        val frontendOrigin = corsProperties.allowedOrigins.first()
        mockMvc.perform(
            post("/api/auth/login")
                .header("Origin", frontendOrigin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload("demo", "demo-password")),
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Access-Control-Allow-Origin", frontendOrigin))
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
    }

    @Test
    fun `logout invalidates active session`() {
        val session = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload("demo@example.com", "demo-password")),
        )
            .andExpect(status().isOk)
            .andReturn()
            .request
            .session as MockHttpSession

        mockMvc.perform(post("/api/auth/logout").session(session))
            .andExpect(status().isNoContent)
    }
}
