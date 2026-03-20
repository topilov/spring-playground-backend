package me.topilov.springplayground

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
@Sql(
    statements = [
        "UPDATE user_profile SET display_name = 'Demo User', bio = 'Session-backed example profile', updated_at = CURRENT_TIMESTAMP WHERE user_id = 1",
    ],
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
)
class ProfileIntegrationTest : PostgresIntegrationTestSupport() {
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
    fun `authenticated user can fetch current profile`() {
        val session = authenticate("demo")

        mockMvc.perform(get("/api/profile/me").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(1))
            .andExpect(jsonPath("$.username").value("demo"))
            .andExpect(jsonPath("$.email").value("demo@example.com"))
            .andExpect(jsonPath("$.displayName").value("Demo User"))
    }

    @Test
    fun `authenticated user can update current profile`() {
        val session = authenticate("demo@example.com")

        mockMvc.perform(
            put("/api/profile/me")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"displayName":"Updated Demo","bio":"Updated from integration test"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.displayName").value("Updated Demo"))
            .andExpect(jsonPath("$.bio").value("Updated from integration test"))
    }

    private fun authenticate(usernameOrEmail: String): MockHttpSession =
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"usernameOrEmail":"$usernameOrEmail","password":"demo-password"}"""),
        )
            .andExpect(status().isOk)
            .andReturn()
            .request
            .session as MockHttpSession
}
