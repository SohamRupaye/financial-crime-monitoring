package com.sohamrupaye.financialcrimemonitoring;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A throwaway PostgreSQL container for tests that need persistence.
 *
 * <p>{@code @ServiceConnection} points {@code spring.datasource.*} at it, so
 * tests never read the development values in {@code application.properties}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Same major version as the compose files under {@code docker/}. */
    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("postgres:17-alpine");

    @Bean
    @ServiceConnection
    public PostgreSQLContainer postgresContainer() {
        // Reused across the suite; stopped at JVM shutdown.
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }
}
