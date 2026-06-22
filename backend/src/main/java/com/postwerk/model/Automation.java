package com.postwerk.model;

import com.postwerk.model.enums.AutomationKind;
import com.postwerk.model.enums.AutomationStatus;
import com.postwerk.model.enums.AutomationType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity representing an email automation workflow.
 *
 * <p>An automation consists of a directed graph of {@link AutomationNode}s connected by
 * {@link AutomationEdge}s. It is scoped to one or more email accounts and can be
 * in ACTIVE or PAUSED status. The visual layout is stored as JSONB in {@code flowData}.
 * Soft-deleted via {@code deletedAt} column.</p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "automations")
@SQLRestriction("deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Automation {

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

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AutomationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AutomationKind kind = AutomationKind.AUTOMATION;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AutomationStatus status;

    @Column(name = "account_ids", columnDefinition = "UUID[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private UUID[] accountIds;

    @Column(nullable = false, length = 7)
    private String color;

    @Column(name = "flow_data", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String flowData;

    @Column(columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String constants;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean locked = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean hidden = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "automation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AutomationNode> nodes;

    @OneToMany(mappedBy = "automation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AutomationEdge> edges;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (type == null) type = AutomationType.EMAIL;
        if (kind == null) kind = AutomationKind.AUTOMATION;
        if (status == null) status = AutomationStatus.PAUSED;
        if (color == null) color = "#3b82f6";
        if (flowData == null) flowData = "{}";
        if (constants == null) constants = "{}";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
