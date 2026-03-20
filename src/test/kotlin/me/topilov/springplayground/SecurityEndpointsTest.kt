package me.topilov.springplayground

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.web.context.WebApplicationContext
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity

@SpringBootTest
@Sql(
    statements = [
        "UPDATE user_profile SET display_name = 'Demo User', bio = 'Session-backed example profile', updated_at = CURRENT_TIMESTAMP WHERE user_id = 1",
    ],
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
)
class SecurityEndpointsTest : PostgresIntegrationTestSupport() {
    @Autowired
    lateinit var webApplicationContext: WebApplicationContext

    lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()
    }

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
    fun `profile endpoint requires authentication`() {
        mockMvc.perform(get("/api/profile/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `login by username creates authenticated session`() {
        val result = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"usernameOrEmail":"demo","password":"demo-password"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.username").value("demo"))
            .andReturn()

        assertThat(result.request.session).isInstanceOf(MockHttpSession::class.java)
    }

    @Test
    fun `logout invalidates active session`() {
        val session = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"usernameOrEmail":"demo@example.com","password":"demo-password"}"""),
        )
            .andExpect(status().isOk)
            .andReturn()
            .request
            .session as MockHttpSession

        mockMvc.perform(post("/api/auth/logout").session(session))
            .andExpect(status().isNoContent)
    }
}
