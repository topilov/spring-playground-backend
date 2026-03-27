package me.topilov.springplayground

import me.topilov.springplayground.auth.TestEmailVerificationConfiguration
import me.topilov.springplayground.auth.TestMailConfiguration
import me.topilov.springplayground.auth.TestPasswordResetConfiguration
import me.topilov.springplayground.auth.TestTwoFactorConfiguration
import me.topilov.springplayground.auth.repository.AuthUserRepository
import me.topilov.springplayground.auth.passkey.TestPasskeyConfiguration
import me.topilov.springplayground.profile.TestPendingEmailChangeConfiguration
import me.topilov.springplayground.profile.repository.UserProfileRepository
import me.topilov.springplayground.protection.TestProtectionConfiguration
import me.topilov.springplayground.telegram.TestTelegramConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer

@SpringBootTest
@ActiveProfiles("local")
@Import(
    TestMailConfiguration::class,
    TestEmailVerificationConfiguration::class,
    TestPasswordResetConfiguration::class,
    TestPasskeyConfiguration::class,
    TestTwoFactorConfiguration::class,
    TestPendingEmailChangeConfiguration::class,
    TestProtectionConfiguration::class,
    TestTelegramConfiguration::class,
)
class LocalProfileDevelopmentIntegrationTest {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var authUserRepository: AuthUserRepository

    @Autowired
    private lateinit var userProfileRepository: UserProfileRepository

    @org.junit.jupiter.api.BeforeEach
    fun setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()
    }

    @Test
    fun `local profile ensures demo account exists with profile`() {
        val demoUser = authUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("demo", "demo@example.com")

        assertThat(demoUser).isPresent
        assertThat(demoUser.get().emailVerified).isTrue()
        assertThat(demoUser.get().enabled).isTrue()
        assertThat(userProfileRepository.findByUserId(requireNotNull(demoUser.get().id))).isPresent()
    }

    @Test
    fun `local profile login succeeds without captcha token`() {
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"usernameOrEmail":"demo","password":"demo-password"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.username").value("demo"))
    }

    companion object {
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17-alpine").apply {
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.data.redis.url") { "redis://localhost:6379" }
        }
    }
}
