package com.sohamrupaye.financialcrimemonitoring.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Switches on the auditing that populates {@code createdAt} / {@code updatedAt}
 * on {@code BaseEntity}.
 *
 * <p>Without this annotation somewhere in the scanned packages, the
 * {@code @CreatedDate} and {@code @LastModifiedDate} fields simply stay null and
 * the {@code NOT NULL} columns reject the insert. It is an easy hour to lose.
 *
 * <p>It sits in its own class rather than on the main application class so that
 * {@code @SpringBootApplication} stays a bare entry point, and so a slice test can
 * import just this configuration when it needs auditing.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
