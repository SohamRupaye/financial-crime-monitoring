package com.sohamrupaye.financialcrimemonitoring.model;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Shared identity and audit columns for every entity.
 *
 * <p>Timestamps are filled by Spring Data's auditing listener, switched on in
 * {@code JpaAuditingConfig}. For AML work an audit trail is not optional, so it
 * lives here rather than being re-added per entity and forgotten somewhere.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseEntity {

    /**
     * Surrogate key, never shown to API clients — that is what each entity's own
     * reference field is for.
     *
     * <p>{@code IDENTITY} disables JDBC batch inserts. Switch to a
     * {@code SEQUENCE} generator if bulk ingestion ever needs the throughput.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseEntity that)) {
            return false;
        }
        // Two unsaved entities are only equal if they are the same object,
        // which the identity check above already covered.
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        // Constant for unsaved instances, so an entity added to a HashSet before
        // saving can still be found after the ID is assigned.
        return id == null ? 31 : id.hashCode();
    }
}
