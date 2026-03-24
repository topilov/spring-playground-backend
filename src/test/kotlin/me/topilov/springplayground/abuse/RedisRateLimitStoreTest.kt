package me.topilov.springplayground.abuse

import me.topilov.springplayground.abuse.store.RedisRateLimitStore
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
class RedisRateLimitStoreTest {
    private lateinit var connectionFactory: LettuceConnectionFactory
    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var store: RedisRateLimitStore

    @BeforeEach
    fun setUp() {
        connectionFactory = LettuceConnectionFactory(redis.host, redis.firstMappedPort)
        connectionFactory.afterPropertiesSet()
        redisTemplate = StringRedisTemplate(connectionFactory)
        redisTemplate.afterPropertiesSet()
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
        store = RedisRateLimitStore(redisTemplate)
    }

    @Test
    fun `increment tracks count and ttl`() {
        val first = store.increment("abuse:test:key", Duration.ofMinutes(1))
        val second = store.increment("abuse:test:key", Duration.ofMinutes(1))

        assertThat(first.count).isEqualTo(1)
        assertThat(second.count).isEqualTo(2)
        assertThat(second.expiresIn).isPositive()
        assertThat(store.get("abuse:test:key")?.count).isEqualTo(2)
    }

    @Test
    fun `reset clears stored counter`() {
        store.increment("abuse:test:key", Duration.ofMinutes(1))

        store.reset("abuse:test:key")

        assertThat(store.get("abuse:test:key")).isNull()
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
