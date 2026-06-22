package com.postwerk.model;

import com.postwerk.model.enums.NotificationCategory;
import com.postwerk.model.enums.NotificationSeverity;
import com.postwerk.model.enums.NotificationType;
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
 * A single user-facing notification — one row per recipient (events fan out to owner + org
 * admins). The DB is the source of truth (offline-safe inbox with read/unread state); SSE/poll
 * are delivery optimizations. Text is stored as i18n key + JSON params so it renders in the
 * recipient's language. See {@code doc/NOTIFICATION_SYSTEM_DESIGN.md}.
 *
 * @since 1.0
 */
@Entity
@Table(name = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Recipient. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Context organization (nullable for purely personal events). */
    @Column(name = "organization_id")
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationSeverity severity;

    /** i18n key for the title (rendered in the recipient's language). */
    @Column(name = "title_key", nullable = false, length = 120)
    private String titleKey;

    /** i18n key for the body (nullable). */
    @Column(name = "body_key", length = 120)
    private String bodyKey;

    /** i18n interpolation params (JSON object). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", columnDefinition = "jsonb")
    private String params;

    /** Deep link the client navigates to on click. */
    @Column(name = "link_url", length = 512)
    private String linkUrl;

    /** Entity references for the client (JSON object), e.g. {"pendingActionId":"..."}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    /** Per-recipient dedup/cooldown key ({@code type:entityId}); null = never deduped. */
    @Column(name = "dedup_key", length = 200)
    private String dedupKey;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (params == null) params = "{}";
        if (payload == null) payload = "{}";
    }
}
