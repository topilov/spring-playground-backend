package me.topilov.springplayground.profile.emailchange

import me.topilov.springplayground.mail.MailProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration

@Testcontainers
class RedisPendingEmailChangeTokenStoreTest {
    private lateinit var connectionFactory: LettuceConnectionFactory
    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var store: RedisPendingEmailChangeTokenStore

    @BeforeEach
    fun setUp() {
        connectionFactory = LettuceConnectionFactory(redis.host, redis.firstMappedPort)
        connectionFactory.afterPropertiesSet()
        redisTemplate = StringRedisTemplate(connectionFactory)
        redisTemplate.afterPropertiesSet()
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
        store = RedisPendingEmailChangeTokenStore(
            redisTemplate = redisTemplate,
            mailProperties = MailProperties(emailVerificationTtl = Duration.ofHours(24)),
        )
    }

    @Test
    fun `activate find and invalidate token`() {
        store.activateToken(userId = 42, newEmail = "new@example.com", rawToken = "raw-token")

        assertThat(store.findPendingChange("raw-token")).isEqualTo(
            PendingEmailChange(
                userId = 42,
                newEmail = "new@example.com",
            ),
        )

        store.invalidateToken("raw-token")

        assertThat(store.findPendingChange("raw-token")).isNull()
    }

    @Test
    fun `invalidate all tokens removes every active token for the user`() {
        store.activateToken(userId = 7, newEmail = "first@example.com", rawToken = "first-token")
        store.activateToken(userId = 7, newEmail = "second@example.com", rawToken = "second-token")

        store.invalidateAllTokensForUser(7)

        assertThat(store.findPendingChange("first-token")).isNull()
        assertThat(store.findPendingChange("second-token")).isNull()
    }

    @org.junit.jupiter.api.AfterEach
    fun tearDown() {
        connectionFactory.destroy()
    }

    companion object {
        @Container
        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").withExposedPorts(6379)

        @BeforeAll
        @JvmStatic
        fun startRedis() {
            redis.start()
        }
    }
}
