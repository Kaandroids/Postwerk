package com.postwerk.model;

import com.postwerk.config.VectorType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a single knowledge-base entry — a filled instance of the KB's
 * parameter-set schema, embedded for hybrid (vector + full-text) retrieval.
 *
 * <p>{@code data} holds the field values (JSONB). {@code embedding} is the 3072-dim pgvector built
 * from the concatenated {@code embed:true} fields; {@code searchText} is the concatenated
 * {@code keyword:true} fields backing the full-text index. {@code embeddingDirty} flags rows awaiting
 * (re-)embedding by the async worker. No soft-delete — entries are hard-deleted with their KB
 * (FK {@code ON DELETE CASCADE}).</p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "knowledge_base_entries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "knowledge_base_id", nullable = false)
    private UUID knowledgeBaseId;

    /** Denormalized org scope (mirrors the KB) so entry queries stay org-safe without a join. */
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB", nullable = false)
    private String data;

    @Convert(converter = VectorType.class)
    @Column(name = "embedding", columnDefinition = "vector(3072)")
    private float[] embedding;

    /** Concatenated keyword-field values backing the GIN full-text index (built by the service). */
    @Column(name = "search_text", columnDefinition = "TEXT")
    private String searchText;

    /** Hash of the embed-field text — lets re-import skip re-embedding unchanged rows. */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "embedding_dirty", nullable = false)
    @Builder.Default
    private boolean embeddingDirty = true;

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
