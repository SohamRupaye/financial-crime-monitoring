package com.sohamrupaye.financialcrimemonitoring.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Switches on the auditing that populates {@code createdAt} / {@code updatedAt}
 * on {@code BaseEntity}.
 *
 * <p>Without it the timestamp fields stay null and the {@code NOT NULL} columns
 * reject the insert. Its own class rather than the application class so a slice
 * test can import just this when it needs auditing.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
