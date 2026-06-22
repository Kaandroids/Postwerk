package com.postwerk.model;

import com.postwerk.model.enums.ApprovalStatus;
import com.postwerk.model.enums.NodeType;
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
 * A side effect that an action node resolved during a live (supervised-mode) automation run but
 * parked for human approval instead of performing. The {@code actionDetail} holds the fully
 * resolved payload (rendered subject/body, recipient, folder, url, …) so the user approves exactly
 * what will happen, and so it can be executed verbatim on approval without re-resolving variables.
 *
 * @since 1.0
 */
@Entity
@Table(name = "pending_actions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Owner of the automation (creator of the parked action). */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Owning organization — scopes the approval inbox in the multi-tenant model (#4). */
    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "automation_id", nullable = false)
    private UUID automationId;

    @Column(name = "email_id")
    private UUID emailId;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 32)
    private NodeType nodeType;

    @Column(name = "node_label", length = 255)
    private String nodeLabel;

    /** Resolved action payload (JSON) — the same detail map the dry-run pass produced. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "action_detail", nullable = false, columnDefinition = "jsonb")
    private String actionDetail;

    /** Resolved variable context (JSON) at park time, used to re-execute the node verbatim on approval. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_snapshot", columnDefinition = "jsonb")
    private String contextSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = ApprovalStatus.PENDING;
    }
}
