package me.topilov.springplayground

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class SpringPlaygroundApplicationTests : PostgresIntegrationTestSupport() {
    @Test
    fun contextLoads() {
    }
}
