package me.topilov.springplayground

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import me.topilov.springplayground.abuse.InMemoryCooldownStore
import me.topilov.springplayground.abuse.InMemoryRateLimitStore
import me.topilov.springplayground.abuse.TestAbuseProtectionConfiguration
import me.topilov.springplayground.auth.*
import me.topilov.springplayground.auth.passkey.InMemoryPasskeyCeremonyStore
import me.topilov.springplayground.auth.passkey.TestPasskeyConfiguration
import me.topilov.springplayground.config.CorsProperties
import me.topilov.springplayground.mail.MailProperties
import me.topilov.springplayground.profile.InMemoryPendingEmailChangeTokenStore
import me.topilov.springplayground.profile.TestPendingEmailChangeConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.net.URLDecoder
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@SpringBootTest
@Import(
    TestMailConfiguration::class,
    TestEmailVerificationConfiguration::class,
    TestPasswordResetConfiguration::class,
    TestPasskeyConfiguration::class,
    TestTwoFactorConfiguration::class,
    TestPendingEmailChangeConfiguration::class,
    TestAbuseProtectionConfiguration::class,
)
@Sql(
    statements = [
        "DELETE FROM auth_totp_backup_code",
        "DELETE FROM auth_totp_credential",
        "DELETE FROM auth_passkey_credential",
        "UPDATE auth_user SET webauthn_user_handle = NULL",
        "UPDATE auth_user SET username = 'demo', email = 'demo@example.com', email_verified = TRUE, enabled = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = 1",
        "UPDATE auth_user SET password_hash = '\$2y\$10\$R51kCmlq52SEJcVep3uDtOxTXp0r9jPwGa5oQQvRuMQA84PVwCjrK', updated_at = CURRENT_TIMESTAMP WHERE id = 1",
        "UPDATE user_profile SET display_name = 'Demo User', bio = 'Session-backed example profile', updated_at = CURRENT_TIMESTAMP WHERE user_id = 1",
    ],
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
)
class SecurityEndpointsTest : PostgresIntegrationTestSupport() {
    @Autowired
    lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var recordingEmailService: RecordingEmailService

    @Autowired
    lateinit var inMemoryEmailVerificationTokenStore: InMemoryEmailVerificationTokenStore

    @Autowired
    lateinit var inMemoryPasswordResetTokenStore: InMemoryPasswordResetTokenStore

    @Autowired
    lateinit var inMemoryPendingEmailChangeTokenStore: InMemoryPendingEmailChangeTokenStore

    @Autowired
    lateinit var inMemoryPasskeyCeremonyStore: InMemoryPasskeyCeremonyStore

    @Autowired
    lateinit var inMemoryTwoFactorLoginChallengeStore: InMemoryTwoFactorLoginChallengeStore

    @Autowired
    lateinit var inMemoryRateLimitStore: InMemoryRateLimitStore

    @Autowired
    lateinit var inMemoryCooldownStore: InMemoryCooldownStore

    @Autowired
    lateinit var corsProperties: CorsProperties

    @Autowired
    lateinit var mailProperties: MailProperties

    lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        recordingEmailService.clear()
        inMemoryEmailVerificationTokenStore.clear()
        inMemoryPasswordResetTokenStore.clear()
        inMemoryPendingEmailChangeTokenStore.clear()
        inMemoryPasskeyCeremonyStore.clear()
        inMemoryTwoFactorLoginChallengeStore.clear()
        inMemoryRateLimitStore.clear()
        inMemoryCooldownStore.clear()
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
    fun `authenticated user can enable inspect regenerate backup codes and disable totp`() {
        val session = loginSession("demo", "demo-password")

        mockMvc.perform(get("/api/auth/2fa/status").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.pendingSetup").value(false))
            .andExpect(jsonPath("$.backupCodesRemaining").value(0))

        val setupResult = mockMvc.perform(
            post("/api/auth/2fa/setup/start")
                .session(session),
        )
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

        val regenerateResult = mockMvc.perform(
            post("/api/auth/2fa/backup-codes/regenerate")
                .session(session),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.backupCodes.length()").value(10))
            .andReturn()

        val regeneratedBackupCode = jsonField(regenerateResult, "backupCodes.0")
        assertThat(regeneratedBackupCode).isNotEqualTo(firstBackupCode)

        mockMvc.perform(get("/api/auth/2fa/status").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.backupCodesRemaining").value(10))

        mockMvc.perform(
            post("/api/auth/2fa/disable")
                .session(session),
        )
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
            .andExpect(jsonPath("$.publicKey.publicKey").doesNotExist())
            .andExpect(jsonPath("$.publicKey.user.id").isString)
            .andExpect(jsonPath("$.publicKey.excludeCredentials[0].id").isString)
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
                .content(passkeyLoginOptionsPayload()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ceremonyId").isString)
            .andExpect(jsonPath("$.publicKey.challenge").isString)
            .andExpect(jsonPath("$.publicKey.publicKey").doesNotExist())
            .andExpect(jsonPath("$.publicKey.allowCredentials[0].id").isString)
            .andReturn()

        val ceremonyId = jsonField(optionsResult, "ceremonyId")

        val verifyResult = mockMvc.perform(
            post("/api/auth/passkey-login/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "ceremonyId":"$ceremonyId",
                      "captchaToken":"test-captcha-token",
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
                    ${registerPayload(username, email, password)}
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

    private fun totpCredentialExistsForUser(userId: Long): Boolean = jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*) > 0
        FROM auth_totp_credential
        WHERE user_id = ?
        """.trimIndent(),
        Boolean::class.java,
        userId,
    ) ?: false

    private fun activeBackupCodeCountForUser(userId: Long): Int = jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM auth_totp_backup_code backup_code
        JOIN auth_totp_credential credential ON credential.id = backup_code.totp_credential_id
        WHERE credential.user_id = ?
          AND backup_code.used_at IS NULL
        """.trimIndent(),
        Int::class.java,
        userId,
    ) ?: 0

    private fun storedBackupCodeHashesForUser(userId: Long): List<String> = jdbcTemplate.queryForList(
        """
        SELECT backup_code.code_hash
        FROM auth_totp_backup_code backup_code
        JOIN auth_totp_credential credential ON credential.id = backup_code.totp_credential_id
        WHERE credential.user_id = ?
        """.trimIndent(),
        String::class.java,
        userId,
    ).filterNotNull()

    private fun extractToken(message: RecordingEmailService.SentEmail): String {
        val html = message.url
        val tokenValue = Regex("""token=([^"&]+)""")
            .find(html)
            ?.groupValues
            ?.get(1)
            ?: throw AssertionError("Reset token was not found in email body: $html")

        return URLDecoder.decode(tokenValue, StandardCharsets.UTF_8)
    }

    private fun uniqueSuffix(): String = System.nanoTime().toString()

    private fun enableTotpForDemo(): List<String> {
        val session = loginSession("demo", "demo-password")
        val secret = jsonField(
            mockMvc.perform(post("/api/auth/2fa/setup/start").session(session))
                .andExpect(status().isOk)
                .andReturn(),
            "secret",
        )

        val response = mockMvc.perform(
            post("/api/auth/2fa/setup/confirm")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"${currentTotpCode(secret)}"}"""),
        )
            .andExpect(status().isOk)
            .andReturn()

        mockMvc.perform(post("/api/auth/logout").session(session))
            .andExpect(status().isNoContent)

        return objectMapper.readTree(response.response.contentAsString)["backupCodes"]
            .map { it.asText() }
    }

    private fun currentTotpCode(secret: String): String {
        val counter = Instant.now().epochSecond / 30
        val counterBytes = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(decodeBase32(secret), "HmacSHA1"))
        val hash = mac.doFinal(counterBytes)
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        return "%06d".format(binary % 1_000_000)
    }

    private fun decodeBase32(value: String): ByteArray {
        val normalized = value.trim().uppercase().filterNot { it == '=' || it.isWhitespace() }
        val output = ArrayList<Byte>()
        var buffer = 0
        var bitsLeft = 0

        normalized.forEach { character ->
            val current = BASE32_ALPHABET.indexOf(character)
            require(current >= 0) { "Unsupported base32 character: $character" }
            buffer = (buffer shl 5) or current
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output += ((buffer shr bitsLeft) and 0xff).toByte()
            }
        }

        return output.toByteArray()
    }

    private fun loginSession(usernameOrEmail: String, password: String): MockHttpSession =
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload(usernameOrEmail, password)),
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
                .content(registerPayload(username, email, password)),
        )
            .andExpect(status().isOk)

        val token = extractToken(recordingEmailService.sentEmails().single())
        recordingEmailService.clear()

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

    private fun registerPayload(username: String, email: String, password: String): String =
        """{"username":"$username","email":"$email","password":"$password","captchaToken":"test-captcha-token"}"""

    private fun loginPayload(usernameOrEmail: String, password: String): String =
        """{"usernameOrEmail":"$usernameOrEmail","password":"$password","captchaToken":"test-captcha-token"}"""

    private fun emailCaptchaPayload(email: String): String =
        """{"email":"$email","captchaToken":"test-captcha-token"}"""

    private fun twoFactorPayload(loginChallengeId: String, code: String): String =
        """{"loginChallengeId":"$loginChallengeId","code":"$code","captchaToken":"test-captcha-token"}"""

    private fun twoFactorBackupPayload(loginChallengeId: String, backupCode: String): String =
        """{"loginChallengeId":"$loginChallengeId","backupCode":"$backupCode","captchaToken":"test-captcha-token"}"""

    private fun passkeyLoginOptionsPayload(usernameOrEmail: String? = null): String =
        if (usernameOrEmail == null) {
            """{"captchaToken":"test-captcha-token"}"""
        } else {
            """{"usernameOrEmail":"$usernameOrEmail","captchaToken":"test-captcha-token"}"""
        }

    private fun resetPasswordPayload(token: String, newPassword: String): String =
        """{"token":"$token","newPassword":"$newPassword","captchaToken":"test-captcha-token"}"""

    private fun emailChangeVerifyPayload(token: String): String =
        """{"token":"$token","captchaToken":"test-captcha-token"}"""

    private fun pathSegmentIndex(value: String): Int = value.toInt()

    companion object {
        private val objectMapper = jacksonObjectMapper()
        private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    }
}
