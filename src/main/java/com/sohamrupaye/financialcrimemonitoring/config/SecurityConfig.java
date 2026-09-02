package com.sohamrupaye.financialcrimemonitoring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Web security.
 *
 * <p><strong>The API is currently open. This is temporary scaffolding.</strong>
 * There is no {@code User} entity or JWT support yet, so requiring
 * authentication would leave no way to authenticate. Until section 12 of the
 * readme is built, treat this service as unauthenticated and do not point it at
 * real data.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Replaces the auto-configured chain, which locks everything behind Basic. */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Safe for a token-authenticated API with no cookie session.
                // Never disable it for a browser app that authenticates by cookie.
                .csrf(csrf -> csrf.disable())

                // No server-side session; required before JWT.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // Liveness for the container healthcheck in
                        // docker/production/docker-compose.yml.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()

                        // TODO: replace with .authenticated() and add JWT filter.
                        .requestMatchers("/api/**").permitAll()

                        // Default-deny: a new endpoint is protected until
                        // someone deliberately opens it.
                        .anyRequest().authenticated())

                .httpBasic(basic -> {
                })
                .build();
    }
}
