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
 * JPA entity representing a knowledge base — org-scoped reference data that the
 * {@code VECTOR_SEARCH} automation node retrieves candidates from.
 *
 * <p>The field schema is borrowed from an existing {@link ParameterSet} ({@code parameterSetId}),
 * so the same schema concept is reused across EXTRACT / integration I/O / KB (DRY). {@code fieldRoles}
 * is a KB-level overlay marking which parameter-set fields are embedded (semantic) and which are
 * keyword (full-text); ParameterSet itself stays pure. Entries are stored as {@link KnowledgeBaseEntry}
 * rows. Soft-deleted via {@code deletedAt}.</p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "knowledge_bases")
@SQLRestriction("deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Owning organization — the scoping key in the multi-tenant model (#4). */
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** Creator — retained for GDPR export and marketplace clone-by-author. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** The borrowed field schema; {@link #fieldRoles} keys must exist in this set. */
    @Column(name = "parameter_set_id", nullable = false)
    private UUID parameterSetId;

    /**
     * KB-level role overlay, a JSON object keyed by parameter-set field name:
     * {@code { "<field>": { "embed": <bool>, "keyword": <bool> } }}. At least one field must be
     * {@code embed:true} (validated in the service, not here).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_roles", columnDefinition = "JSONB", nullable = false)
    private String fieldRoles;

    /** Optional natural-key field name used to upsert on CSV re-import (null → full replace). */
    @Column(name = "unique_field", length = 100)
    private String uniqueField;

    @Column(nullable = false)
    @Builder.Default
    private boolean locked = false;

    /**
     * Content-hidden flag for KBs materialized from a PRIVATE marketplace listing — the get/entries
     * API refuses to return {@code data} when true (mirrors {@code Automation.hidden}).
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean hidden = false;

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
        if (fieldRoles == null) {
            fieldRoles = "{}";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
