package me.topilov.springplayground.abuse

import me.topilov.springplayground.abuse.store.RedisCooldownStore
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
class RedisCooldownStoreTest {
    private lateinit var connectionFactory: LettuceConnectionFactory
    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var store: RedisCooldownStore

    @BeforeEach
    fun setUp() {
        connectionFactory = LettuceConnectionFactory(redis.host, redis.firstMappedPort)
        connectionFactory.afterPropertiesSet()
        redisTemplate = StringRedisTemplate(connectionFactory)
        redisTemplate.afterPropertiesSet()
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
        store = RedisCooldownStore(redisTemplate)
    }

    @Test
    fun `activate if absent returns remaining ttl when cooldown already exists`() {
        val first = store.activateIfAbsent("abuse:cooldown:key", Duration.ofMinutes(1))
        val second = store.activateIfAbsent("abuse:cooldown:key", Duration.ofMinutes(1))

        assertThat(first).isNull()
        assertThat(second).isNotNull
        assertThat(second).isPositive()
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
