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
 * <p>Adding {@code spring-boot-starter-security} to the classpath locks down every
 * endpoint behind HTTP Basic with a password printed at startup. That default is
 * why an explicit chain is needed as soon as there is an API to call.
 *
 * <p><strong>The API is currently open. This is temporary scaffolding.</strong>
 * There is no {@code User} entity or JWT support yet, so requiring authentication
 * would leave no way to authenticate. Section 12 of the readme calls for JWT plus
 * the ANALYST / INVESTIGATOR / ADMIN roles; until that exists, treat this service
 * as unauthenticated and do not point it at real data.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * One {@code SecurityFilterChain} bean replaces the auto-configured default.
     *
     * <p>The lambda style below is the only option in Spring Security 6+; the old
     * chained {@code .and()} calls and {@code WebSecurityConfigurerAdapter} are
     * both gone, which is why older tutorials will not compile here.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Safe to disable for a token-authenticated REST API with no
                // cookie-based session. Never disable it for a browser app that
                // authenticates with cookies.
                .csrf(csrf -> csrf.disable())

                // No server-side session; every request must stand alone. This is
                // what "stateless" means in practice and is required before JWT.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // Liveness for the container healthcheck in
                        // docker/production/docker-compose.yml.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()

                        // TODO: replace with .authenticated() and add JWT filter.
                        .requestMatchers("/api/**").permitAll()

                        // Anything not listed above still requires authentication.
                        // Default-deny: new endpoints are protected until someone
                        // deliberately opens them.
                        .anyRequest().authenticated())

                .httpBasic(basic -> {
                })
                .build();
    }
}
