package me.topilov.springplayground.auth

import me.topilov.springplayground.auth.service.RedisTwoFactorLoginChallengeStore
import me.topilov.springplayground.auth.service.StoredTwoFactorLoginChallenge
import me.topilov.springplayground.config.TwoFactorProperties
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
import java.time.Instant

@Testcontainers
class RedisTwoFactorLoginChallengeStoreTest {
    private lateinit var connectionFactory: LettuceConnectionFactory
    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var store: RedisTwoFactorLoginChallengeStore

    @BeforeEach
    fun setUp() {
        connectionFactory = LettuceConnectionFactory(redis.host, redis.firstMappedPort)
        connectionFactory.afterPropertiesSet()
        redisTemplate = StringRedisTemplate(connectionFactory)
        redisTemplate.afterPropertiesSet()
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
        store = RedisTwoFactorLoginChallengeStore(
            redisTemplate = redisTemplate,
            twoFactorProperties = TwoFactorProperties(loginChallengeTtl = Duration.ofMinutes(5)),
        )
    }

    @Test
    fun `save find and consume challenge`() {
        val challenge = StoredTwoFactorLoginChallenge(
            loginChallengeId = "challenge-1",
            userId = 42,
            expiresAt = Instant.now().plusSeconds(300),
        )

        store.save(challenge)

        assertThat(store.find("challenge-1")).isEqualTo(challenge)
        assertThat(store.consume("challenge-1")).isEqualTo(challenge)
        assertThat(store.find("challenge-1")).isNull()
        assertThat(store.consume("challenge-1")).isNull()
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
