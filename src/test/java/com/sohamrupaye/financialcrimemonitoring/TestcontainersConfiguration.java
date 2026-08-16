package com.sohamrupaye.financialcrimemonitoring;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts a throwaway PostgreSQL container for integration tests.
 *
 * <p>{@code @ServiceConnection} points {@code spring.datasource.*} at the
 * container, so tests never read the development values in
 * {@code application.properties} and need no running database of their own.
 *
 * <p>Import it from any test that needs persistence:
 * {@code @Import(TestcontainersConfiguration.class)}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /**
     * Kept in step with the image used by the compose files under {@code docker/}
     * so tests and deployments exercise the same PostgreSQL major version.
     */
    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("postgres:17-alpine");

    @Bean
    @ServiceConnection
    public PostgreSQLContainer postgresContainer() {
        // Reused across the whole suite; Testcontainers stops it at JVM shutdown.
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }
}
