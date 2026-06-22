package com.postwerk.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a reusable email reply/forward template.
 *
 * <p>Templates contain a subject and body with placeholder syntax (e.g., {@code {{name}}})
 * that are resolved at runtime using extracted data from EXTRACT nodes.
 * Optionally linked to a {@link ParameterSet} for structured parameter definitions.
 * Soft-deleted via {@code deletedAt} column.</p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "templates")
@SQLRestriction("deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Template {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Owning organization — the scoping key in the multi-tenant model (#4). */
    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    private String params;

    @Column(name = "parameter_set_id")
    private UUID parameterSetId;

    @Column(nullable = false)
    @Builder.Default
    private boolean locked = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (params == null) params = "[]";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
