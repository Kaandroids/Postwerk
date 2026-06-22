package com.postwerk.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing an organization — the tenant boundary in the multi-tenant model (#4).
 *
 * <p>An organization owns all resources (email accounts, automations, categories, templates, …);
 * users join via {@code Membership} with an {@code OrgRole}, and per-mailbox access is granted via
 * {@code MailboxGrant}. The subscription {@link Plan} and quota attach here. Every user gets an
 * auto-created {@code personal} organization on migration, so single-user accounts are simply
 * one-member organizations. Soft-deleted via {@code deletedAt}.</p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "organizations")
@SQLRestriction("deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 140)
    private String slug;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    /** Convenience pointer to the user who created/owns this org (the single user for a personal org). */
    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    /** True for the auto-created single-user workspace; false for collaborative organizations. */
    @Column(nullable = false)
    @Builder.Default
    private boolean personal = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** When suspended by platform staff; {@code null} = active. A suspended org rejects all tenant access. */
    @Column(name = "suspended_at")
    private Instant suspendedAt;

    /** Optional staff-recorded reason for the suspension (support/audit). */
    @Column(name = "suspension_reason", length = 500)
    private String suspensionReason;

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
