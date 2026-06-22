package com.postwerk.model;

import com.postwerk.model.enums.MembershipStatus;
import com.postwerk.model.enums.OrgRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity linking a {@link User} to an {@link Organization} with an {@link OrgRole}
 * (multi-tenant model #4). Unique per (organization, user). A user may hold memberships in
 * many organizations (Slack-style switcher).
 *
 * @since 1.0
 */
@Entity
@Table(name = "memberships", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"organization_id", "user_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrgRole role = OrgRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MembershipStatus status = MembershipStatus.ACTIVE;

    @Column(name = "invited_by_user_id")
    private UUID invitedByUserId;

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
