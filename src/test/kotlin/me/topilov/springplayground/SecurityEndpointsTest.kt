package me.topilov.springplayground

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.mail.Multipart
import jakarta.mail.internet.MimeMessage
import me.topilov.springplayground.auth.InMemoryEmailVerificationTokenStore
import me.topilov.springplayground.auth.InMemoryPasswordResetTokenStore
import me.topilov.springplayground.auth.RecordingJavaMailSender
import me.topilov.springplayground.auth.TestEmailVerificationConfiguration
import me.topilov.springplayground.auth.TestMailConfiguration
import me.topilov.springplayground.auth.TestPasswordResetConfiguration
import me.topilov.springplayground.auth.passkey.InMemoryPasskeyCeremonyStore
import me.topilov.springplayground.auth.passkey.TestPasskeyConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
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
@Import(
    TestMailConfiguration::class,
    TestEmailVerificationConfiguration::class,
    TestPasswordResetConfiguration::class,
    TestPasskeyConfiguration::class,
)
@Sql(
    statements = [
        "DELETE FROM auth_passkey_credential",
        "UPDATE auth_user SET webauthn_user_handle = NULL",
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

    @Autowired
    lateinit var inMemoryEmailVerificationTokenStore: InMemoryEmailVerificationTokenStore

    @Autowired
    lateinit var inMemoryPasswordResetTokenStore: InMemoryPasswordResetTokenStore

    @Autowired
    lateinit var inMemoryPasskeyCeremonyStore: InMemoryPasskeyCeremonyStore

    lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        recordingJavaMailSender.clear()
        inMemoryEmailVerificationTokenStore.clear()
        inMemoryPasswordResetTokenStore.clear()
        inMemoryPasskeyCeremonyStore.clear()
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
            .andExpect(jsonPath("$.paths['/api/auth/passkeys']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/passkeys/register/options']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/passkeys/register/verify']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/passkey-login/options']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/passkey-login/verify']").exists())
            .andExpect(jsonPath("$.paths['/api/profile/me']").exists())
            .andExpect(jsonPath("$.paths['/api/public/ping'].get.responses['400']").doesNotExist())
            .andExpect(jsonPath("$.paths['/api/public/ping'].get.responses['409']").doesNotExist())
            .andExpect(jsonPath("$.paths['/api/profile/me'].get.responses['400']").doesNotExist())
            .andExpect(jsonPath("$.paths['/api/profile/me'].get.responses['409']").doesNotExist())
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
                .content("""{}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ceremonyId").isString)
            .andExpect(jsonPath("$.publicKey.challenge").isString)
            .andReturn()

        val ceremonyId = jsonField(optionsResult, "ceremonyId")

        val verifyResult = mockMvc.perform(
            post("/api/auth/passkey-login/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "ceremonyId":"$ceremonyId",
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

    @Test
    fun `register endpoint is public creates profile and sends welcome email without session`() {
        val unique = uniqueSuffix()
        val username = "new-user-$unique"
        val email = "new-user-$unique@example.com"
        val password = "very-secret-password"

        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"username":"$username","email":"$email","password":"$password"}
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
        assertThat(recordingJavaMailSender.sentMessages().single().subject).contains("Verify")
        assertThat(extractToken(recordingJavaMailSender.sentMessages().single())).isNotBlank()

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"usernameOrEmail":"$email","password":"$password"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
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
    fun `database enforces case insensitive auth identity uniqueness`() {
        assertThrows(DataIntegrityViolationException::class.java) {
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

        assertThrows(DataIntegrityViolationException::class.java) {
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
    fun `password reset tokens are not persisted in postgres`() {
        mockMvc.perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"demo@example.com"}"""),
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
                .content("""{"username":"$username","email":"$email","password":"$password"}"""),
        )
            .andExpect(status().isOk)

        val token = extractToken(recordingJavaMailSender.sentMessages().single())

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
                .content("""{"usernameOrEmail":"$email","password":"$password"}"""),
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
                .content("""{"username":"$username","email":"$email","password":"$password"}"""),
        )
            .andExpect(status().isOk)

        val firstToken = extractToken(recordingJavaMailSender.sentMessages().single())
        recordingJavaMailSender.clear()

        mockMvc.perform(
            post("/api/auth/resend-verification-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accepted").value(true))

        val resentMessage = recordingJavaMailSender.sentMessages().single()
        val secondToken = extractToken(resentMessage)
        assertThat(secondToken).isNotEqualTo(firstToken)

        mockMvc.perform(
            post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$secondToken"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.verified").value(true))

        recordingJavaMailSender.clear()

        mockMvc.perform(
            post("/api/auth/resend-verification-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"missing-$unique@example.com"}"""),
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

        val verificationToken = extractToken(recordingJavaMailSender.sentMessages().single())
        mockMvc.perform(
            post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$verificationToken"}"""),
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

    private fun passwordResetTokenTableExists(): Boolean = jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*) > 0
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'password_reset_token'
        """.trimIndent(),
        Boolean::class.java,
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

    private fun loginSession(usernameOrEmail: String, password: String): MockHttpSession =
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"usernameOrEmail":"$usernameOrEmail","password":"$password"}"""),
        )
            .andExpect(status().isOk)
            .andReturn()
            .request
            .session as MockHttpSession

    private fun createVerifiedUserAndLogin(): MockHttpSession {
        val unique = uniqueSuffix()
        val username = "passkey-user-$unique"
        val email = "passkey-user-$unique@example.com"
        val password = "passkey-password"

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"$username","email":"$email","password":"$password"}"""),
        )
            .andExpect(status().isOk)

        val token = extractToken(recordingJavaMailSender.sentMessages().single())
        recordingJavaMailSender.clear()

        mockMvc.perform(
            post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$token"}"""),
        )
            .andExpect(status().isOk)

        return loginSession(email, password)
    }

    private fun jsonField(result: MvcResult, path: String): String {
        val root = objectMapper.readTree(result.response.contentAsString)
        var node = root
        path.split('.').forEach { part ->
            node = if (part.all(Char::isDigit)) {
                node[pathSegmentIndex(part)]
            } else {
                node[part]
            }
        }
        return node.asText()
    }

    private fun pathSegmentIndex(value: String): Int = value.toInt()

    companion object {
        private val objectMapper = jacksonObjectMapper()
    }
}
