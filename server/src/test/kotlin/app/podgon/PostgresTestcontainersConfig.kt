package app.podgon

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer

/** Реальный PostgreSQL для интеграционных тестов (версия совпадает с production). */
@TestConfiguration(proxyBeanMethods = false)
class PostgresTestcontainersConfig {
    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer = PostgreSQLContainer("postgres:18")
}
