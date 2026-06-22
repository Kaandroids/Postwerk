package com.postwerk.model;

import com.postwerk.config.VectorType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing an email classification category.
 *
 * <p>Categories are used by the CATEGORIZE automation node to classify incoming emails.
 * Each category has a description, optional positive/negative examples for AI training,
 * and a pgvector embedding (3072-dimensional) for cosine-similarity matching.
 * Soft-deleted via {@code deletedAt} column.</p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "categories")
@SQLRestriction("deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Owning organization — the scoping key in the multi-tenant model (#4). */
    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private String color;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "positive_example", columnDefinition = "TEXT")
    private String positiveExample;

    @Column(name = "negative_example", columnDefinition = "TEXT")
    private String negativeExample;

    /** Newline-separated examples learned from approval-inbox corrections (#3c); folded into the embedding. */
    @Column(name = "learned_examples", columnDefinition = "TEXT")
    private String learnedExamples;

    @Convert(converter = VectorType.class)
    @Column(name = "embedding", columnDefinition = "vector(3072)")
    private float[] embedding;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
