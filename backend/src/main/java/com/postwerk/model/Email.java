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
 * JPA entity representing a synced email message.
 *
 * <p>Stores message content (subject, body text/HTML, snippet), addressing (from, to, cc),
 * IMAP metadata (UID, folder, message ID), attachment metadata (JSON), and workflow state
 * (categories, approval status). Soft-deleted via {@code deletedAt} column.</p>
 *
 * <p>Uniquely constrained by {@code (email_account_id, message_id)} to prevent
 * duplicate imports during incremental IMAP sync.</p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "emails", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"email_account_id", "message_id"})
})
@SQLRestriction("deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Email {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "email_account_id", nullable = false)
    private UUID emailAccountId;

    @Column(name = "message_id")
    private String messageId;

    @Column(nullable = false)
    private String folder;

    @Column(name = "from_address")
    private String fromAddress;

    @Column(name = "from_personal")
    private String fromPersonal;

    @Column(name = "to_addresses")
    private String toAddresses;

    @Column(name = "cc_addresses")
    private String ccAddresses;

    @Column(name = "bcc_addresses")
    private String bccAddresses;

    private String subject;

    @Column(name = "body_text", columnDefinition = "TEXT")
    private String bodyText;

    @Column(name = "body_html", columnDefinition = "TEXT")
    private String bodyHtml;

    private String snippet;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "is_starred", nullable = false)
    private boolean isStarred;

    @Column(name = "has_attachments", nullable = false)
    private boolean hasAttachments;

    @Column(columnDefinition = "TEXT")
    private String attachments;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    private Long uid;

    @Column(nullable = false)
    private boolean processed;

    @Column(columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String categories;

    @Column(columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String labels;

    @Column(name = "in_reply_to", length = 500)
    private String inReplyTo;

    @Column(name = "approval_status", length = 20)
    private String approvalStatus;

    @Column(name = "approval_automation_id")
    private UUID approvalAutomationId;

    @Column(name = "approval_node_id")
    private UUID approvalNodeId;

    @Column(name = "approval_requested_at")
    private Instant approvalRequestedAt;

    @Column(name = "approval_resolved_at")
    private Instant approvalResolvedAt;

    @Column(name = "approval_action_config", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String approvalActionConfig;

    /** Permanent (soft) delete marker — hidden everywhere via {@code @SQLRestriction("deleted_at IS NULL")}. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * Trash (Papierkorb) marker. When set, the email has been moved to Trash but is NOT deleted: it stays
     * fully readable and keeps its original {@code folder}/{@code uid} (so IMAP re-sync and attachment fetch
     * still work). The Trash view lists {@code trashed_at IS NOT NULL}; all other folder views exclude it.
     */
    @Column(name = "trashed_at")
    private Instant trashedAt;

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
