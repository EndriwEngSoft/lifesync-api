package com.lifesync.api.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Um Postgres de teste (Testcontainers) ligado ao contexto Spring. Os testes
 * de integracao importam esta classe com {@code @Import}: contextos com a
 * mesma configuracao sao reaproveitados pelo Spring, entao o container sobe
 * uma vez so para todos eles. O Flyway roda as migrations reais.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:16-alpine");
    }
}
