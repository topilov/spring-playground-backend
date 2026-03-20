package me.topilov.springplayground.auth.reset

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
class RedisPasswordResetTokenStoreTest {
    private lateinit var connectionFactory: LettuceConnectionFactory
    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var store: RedisPasswordResetTokenStore

    @BeforeEach
    fun setUp() {
        connectionFactory = LettuceConnectionFactory(redis.host, redis.firstMappedPort)
        connectionFactory.afterPropertiesSet()
        redisTemplate = StringRedisTemplate(connectionFactory)
        redisTemplate.afterPropertiesSet()
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
        store = RedisPasswordResetTokenStore(
            redisTemplate = redisTemplate,
            mailProperties = MailProperties(passwordResetTtl = Duration.ofMinutes(30)),
        )
    }

    @Test
    fun `activate find and invalidate token`() {
        store.activateToken(userId = 42, rawToken = "raw-token")

        assertThat(store.findUserId("raw-token")).isEqualTo(42)

        store.invalidateToken("raw-token")

        assertThat(store.findUserId("raw-token")).isNull()
    }

    @Test
    fun `invalidate all tokens removes every active token for the user`() {
        store.activateToken(userId = 7, rawToken = "first-token")
        store.activateToken(userId = 7, rawToken = "second-token")

        store.invalidateAllTokensForUser(7)

        assertThat(store.findUserId("first-token")).isNull()
        assertThat(store.findUserId("second-token")).isNull()
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
