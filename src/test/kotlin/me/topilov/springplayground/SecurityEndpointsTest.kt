package me.topilov.springplayground

import jakarta.mail.Multipart
import jakarta.mail.internet.MimeMessage
import me.topilov.springplayground.auth.RecordingJavaMailSender
import me.topilov.springplayground.auth.TestMailConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@SpringBootTest
@Import(TestMailConfiguration::class)
@Sql(
    statements = [
        "UPDATE auth_user SET password_hash = '\$2y\$10\$R51kCmlq52SEJcVep3uDtOxTXp0r9jPwGa5oQQvRuMQA84PVwCjrK', updated_at = CURRENT_TIMESTAMP WHERE id = 1",
        "UPDATE user_profile SET display_name = 'Demo User', bio = 'Session-backed example profile', updated_at = CURRENT_TIMESTAMP WHERE user_id = 1",
    ],
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
)
class SecurityEndpointsTest : PostgresIntegrationTestSupport() {
    private val frontendOrigin = "http://localhost:4173"

    @Autowired
    lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var recordingJavaMailSender: RecordingJavaMailSender

    lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        recordingJavaMailSender.clear()
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
    fun `openapi endpoint is public and exposes current api paths`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.openapi").exists())
            .andExpect(jsonPath("$.paths['/api/public/ping']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/logout']").exists())
            .andExpect(jsonPath("$.paths['/api/profile/me']").exists())
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
                .content("""{"usernameOrEmail":"demo","password":"demo-password"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.username").value("demo"))
            .andReturn()

        assertThat(result.request.session).isInstanceOf(MockHttpSession::class.java)
    }

    @Test
    fun `login preflight allows frontend origin`() {
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
        mockMvc.perform(
            post("/api/auth/login")
                .header("Origin", frontendOrigin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"usernameOrEmail":"demo","password":"demo-password"}"""),
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
                .content("""{"usernameOrEmail":"demo@example.com","password":"demo-password"}"""),
        )
            .andExpect(status().isOk)
            .andReturn()
            .request
            .session as MockHttpSession

        mockMvc.perform(post("/api/auth/logout").session(session))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `register endpoint is public creates profile and sends welcome email without session`() {
        val unique = uniqueSuffix()
        val username = "new-user-$unique"
        val email = "new-user-$unique@example.com"

        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"username":"$username","email":"$email","password":"very-secret-password"}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value(username))
            .andExpect(jsonPath("$.email").value(email))
            .andReturn()

        assertThat(result.response.getCookie("JSESSIONID")).isNull()
        assertThat(userExists(email)).isTrue()
        assertThat(profileExists(email)).isTrue()
        assertThat(recordingJavaMailSender.sentMessages()).hasSize(1)
        assertThat(recordingJavaMailSender.sentMessages().single().allRecipients.single().toString()).isEqualTo(email)
        assertThat(recordingJavaMailSender.sentMessages().single().subject).contains("Welcome")
    }

    @Test
    fun `duplicate register request returns conflict`() {
        val unique = uniqueSuffix()
        val username = "duplicate-$unique"
        val email = "duplicate-$unique@example.com"
        val payload = """{"username":"$username","email":"$email","password":"very-secret-password"}"""

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
    fun `forgot password returns accepted for existing and missing email`() {
        mockMvc.perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"demo@example.com"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accepted").value(true))

        assertThat(recordingJavaMailSender.sentMessages()).hasSize(1)
        assertThat(recordingJavaMailSender.sentMessages().single().subject).contains("Reset")

        recordingJavaMailSender.clear()

        mockMvc.perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"missing@example.com"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accepted").value(true))

        assertThat(recordingJavaMailSender.sentMessages()).isEmpty()
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
                .content("""{"username":"$username","email":"$email","password":"$oldPassword"}"""),
        )
            .andExpect(status().isOk)

        recordingJavaMailSender.clear()

        mockMvc.perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email"}"""),
        )
            .andExpect(status().isOk)

        val token = extractToken(recordingJavaMailSender.sentMessages().single())

        mockMvc.perform(
            post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$token","newPassword":"$newPassword"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reset").value(true))

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"usernameOrEmail":"$email","password":"$oldPassword"}"""),
        )
            .andExpect(status().isUnauthorized)

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"usernameOrEmail":"$email","password":"$newPassword"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value(username))
    }

    @Test
    fun `reset password rejects invalid token`() {
        mockMvc.perform(
            post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"invalid-token","newPassword":"new-password-value"}"""),
        )
            .andExpect(status().isBadRequest)
    }

    private fun userExists(email: String): Boolean = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) > 0 FROM auth_user WHERE email = ?",
        Boolean::class.java,
        email,
    ) ?: false

    private fun profileExists(email: String): Boolean = jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*) > 0
        FROM user_profile profile
        JOIN auth_user auth_user ON auth_user.id = profile.user_id
        WHERE auth_user.email = ?
        """.trimIndent(),
        Boolean::class.java,
        email,
    ) ?: false

    private fun extractToken(message: MimeMessage): String {
        val html = message.htmlBody()
        val tokenValue = Regex("""token=([^"&]+)""")
            .find(html)
            ?.groupValues
            ?.get(1)
            ?: throw AssertionError("Reset token was not found in email body: $html")

        return URLDecoder.decode(tokenValue, StandardCharsets.UTF_8)
    }

    private fun MimeMessage.htmlBody(): String {
        val messageContent = content
        return when (messageContent) {
            is String -> messageContent
            is Multipart -> {
                (0 until messageContent.count)
                    .asSequence()
                    .map { index -> messageContent.getBodyPart(index).content }
                    .filterIsInstance<String>()
                    .firstOrNull()
                    ?: ""
            }

            else -> messageContent?.toString().orEmpty()
        }
    }

    private fun uniqueSuffix(): String = System.nanoTime().toString()
}
