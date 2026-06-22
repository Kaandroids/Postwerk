package com.postwerk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing an AI chat conversation for a user.
 *
 * <p>Stores the conversation title and a JSONB array of messages (role + content pairs).
 * Used by the AI assistant chat panel to persist multi-turn conversations.</p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "ai_conversations")
@SQLRestriction("deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Owning organization — the org the conversation took place in (#4). */
    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(length = 200)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String messages;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20) default 'OPEN'", nullable = false)
    @Builder.Default
    private ConversationPhase phase = ConversationPhase.OPEN;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (messages == null) {
            messages = "[]";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
