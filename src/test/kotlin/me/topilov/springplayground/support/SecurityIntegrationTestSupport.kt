package me.topilov.springplayground.support

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import me.topilov.springplayground.PostgresIntegrationTestSupport
import me.topilov.springplayground.auth.InMemoryEmailVerificationTokenStore
import me.topilov.springplayground.auth.InMemoryPasswordResetTokenStore
import me.topilov.springplayground.auth.InMemoryTwoFactorLoginChallengeStore
import me.topilov.springplayground.auth.RecordingEmailService
import me.topilov.springplayground.auth.TestEmailVerificationConfiguration
import me.topilov.springplayground.auth.TestMailConfiguration
import me.topilov.springplayground.auth.TestPasswordResetConfiguration
import me.topilov.springplayground.auth.TestTwoFactorConfiguration
import me.topilov.springplayground.auth.passkey.InMemoryPasskeyCeremonyStore
import me.topilov.springplayground.auth.passkey.TestPasskeyConfiguration
import me.topilov.springplayground.config.CorsProperties
import me.topilov.springplayground.mail.MailProperties
import me.topilov.springplayground.profile.InMemoryPendingEmailChangeTokenStore
import me.topilov.springplayground.profile.TestPendingEmailChangeConfiguration
import me.topilov.springplayground.protection.InMemoryCooldownStore
import me.topilov.springplayground.protection.InMemoryRateLimitStore
import me.topilov.springplayground.protection.TestProtectionConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.net.URLDecoder
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
    TestProtectionConfiguration::class,
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
abstract class SecurityIntegrationTestSupport : PostgresIntegrationTestSupport() {
    @Autowired
    protected lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    @Autowired
    protected lateinit var recordingEmailService: RecordingEmailService

    @Autowired
    protected lateinit var inMemoryEmailVerificationTokenStore: InMemoryEmailVerificationTokenStore

    @Autowired
    protected lateinit var inMemoryPasswordResetTokenStore: InMemoryPasswordResetTokenStore

    @Autowired
    protected lateinit var inMemoryPendingEmailChangeTokenStore: InMemoryPendingEmailChangeTokenStore

    @Autowired
    protected lateinit var inMemoryPasskeyCeremonyStore: InMemoryPasskeyCeremonyStore

    @Autowired
    protected lateinit var inMemoryTwoFactorLoginChallengeStore: InMemoryTwoFactorLoginChallengeStore

    @Autowired
    protected lateinit var inMemoryRateLimitStore: InMemoryRateLimitStore

    @Autowired
    protected lateinit var inMemoryCooldownStore: InMemoryCooldownStore

    @Autowired
    protected lateinit var corsProperties: CorsProperties

    @Autowired
    protected lateinit var mailProperties: MailProperties

    protected lateinit var mockMvc: MockMvc

    @org.junit.jupiter.api.BeforeEach
    fun setUpSecurityIntegrationSupport() {
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

    protected fun userExists(email: String): Boolean = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) > 0 FROM auth_user WHERE email = ?",
        Boolean::class.java,
        email,
    ) ?: false

    protected fun profileExists(email: String): Boolean = jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*) > 0
        FROM user_profile profile
        JOIN auth_user auth_user ON auth_user.id = profile.user_id
        WHERE auth_user.email = ?
        """.trimIndent(),
        Boolean::class.java,
        email,
    ) ?: false

    protected fun passwordResetTokenTableExists(): Boolean = jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*) > 0
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'password_reset_token'
        """.trimIndent(),
        Boolean::class.java,
    ) ?: false

    protected fun totpCredentialExistsForUser(userId: Long): Boolean = jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*) > 0
        FROM auth_totp_credential
        WHERE user_id = ?
        """.trimIndent(),
        Boolean::class.java,
        userId,
    ) ?: false

    protected fun activeBackupCodeCountForUser(userId: Long): Int = jdbcTemplate.queryForObject(
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

    protected fun storedBackupCodeHashesForUser(userId: Long): List<String> = jdbcTemplate.queryForList(
        """
        SELECT backup_code.code_hash
        FROM auth_totp_backup_code backup_code
        JOIN auth_totp_credential credential ON credential.id = backup_code.totp_credential_id
        WHERE credential.user_id = ?
        """.trimIndent(),
        String::class.java,
        userId,
    ).filterNotNull()

    protected fun extractToken(message: RecordingEmailService.SentEmail): String {
        val html = message.url
        val tokenValue = Regex("""token=([^"&]+)""")
            .find(html)
            ?.groupValues
            ?.get(1)
            ?: throw AssertionError("Reset token was not found in email body: $html")

        return URLDecoder.decode(tokenValue, StandardCharsets.UTF_8)
    }

    protected fun uniqueSuffix(): String = System.nanoTime().toString()

    protected fun enableTotpForDemo(): List<String> {
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
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"code":"${currentTotpCode(secret)}"}"""),
        )
            .andExpect(status().isOk)
            .andReturn()

        mockMvc.perform(post("/api/auth/logout").session(session))
            .andExpect(status().isNoContent)

        return objectMapper.readTree(response.response.contentAsString)["backupCodes"]
            .map(JsonNode::asText)
    }

    protected fun currentTotpCode(secret: String): String {
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

    protected fun loginSession(usernameOrEmail: String, password: String): MockHttpSession =
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(loginPayload(usernameOrEmail, password)),
        )
            .andExpect(status().isOk)
            .andReturn()
            .request
            .session as MockHttpSession

    protected fun createVerifiedUserAndLogin(): MockHttpSession {
        val unique = uniqueSuffix()
        val username = "passkey-user-$unique"
        val email = "passkey-user-$unique@example.com"
        val password = "passkey-password"

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(registerPayload(username, email, password)),
        )
            .andExpect(status().isOk)

        val token = extractToken(recordingEmailService.sentEmails().single())
        recordingEmailService.clear()

        mockMvc.perform(
            post("/api/auth/verify-email")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"token":"$token"}"""),
        )
            .andExpect(status().isOk)

        return loginSession(email, password)
    }

    protected fun jsonField(result: MvcResult, path: String): String {
        val root = objectMapper.readTree(result.response.contentAsString)
        var node = root
        path.split('.').forEach { part ->
            node = if (part.all(Char::isDigit)) {
                node[part.toInt()]
            } else {
                node[part]
            }
        }
        return node.asText()
    }

    protected fun registerPayload(username: String, email: String, password: String): String =
        """{"username":"$username","email":"$email","password":"$password","captchaToken":"test-captcha-token"}"""

    protected fun loginPayload(usernameOrEmail: String, password: String): String =
        """{"usernameOrEmail":"$usernameOrEmail","password":"$password","captchaToken":"test-captcha-token"}"""

    protected fun emailCaptchaPayload(email: String): String =
        """{"email":"$email","captchaToken":"test-captcha-token"}"""

    protected fun twoFactorPayload(loginChallengeId: String, code: String): String =
        """{"loginChallengeId":"$loginChallengeId","code":"$code","captchaToken":"test-captcha-token"}"""

    protected fun twoFactorBackupPayload(loginChallengeId: String, backupCode: String): String =
        """{"loginChallengeId":"$loginChallengeId","backupCode":"$backupCode","captchaToken":"test-captcha-token"}"""

    protected fun passkeyLoginOptionsPayload(usernameOrEmail: String? = null): String =
        if (usernameOrEmail == null) {
            """{"captchaToken":"test-captcha-token"}"""
        } else {
            """{"usernameOrEmail":"$usernameOrEmail","captchaToken":"test-captcha-token"}"""
        }

    protected fun resetPasswordPayload(token: String, newPassword: String): String =
        """{"token":"$token","newPassword":"$newPassword","captchaToken":"test-captcha-token"}"""

    protected fun emailChangeVerifyPayload(token: String): String =
        """{"token":"$token","captchaToken":"test-captcha-token"}"""

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

    companion object {
        private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    }
}
