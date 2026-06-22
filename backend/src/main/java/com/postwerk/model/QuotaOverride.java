package com.postwerk.model;

import com.postwerk.model.enums.QuotaOverrideKind;
import com.postwerk.model.enums.QuotaOverrideTargetType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a per-user / per-org exception to a plan's AI cost cap (admin panel).
 *
 * <p>Enforcement is always organization-scoped (#4): a {@code USER} target is resolved to that user's
 * personal organization at creation time, an {@code ORG} target enforces on itself. Both the UI target
 * ({@link #targetType}/{@link #targetId}) and the resolved enforcement {@link #organizationId} are
 * stored so the admin list can render the original target while {@code QuotaService} enforces against
 * the org's plan cap.</p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "quota_overrides")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotaOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 8)
    private QuotaOverrideTargetType targetType;

    /** The UI target id (a user id for {@code USER}, an org id for {@code ORG}). */
    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    /** The resolved enforcement organization (a user target maps to its personal org). */
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private QuotaOverrideKind kind;

    /** Required for {@code CREDIT}/{@code CAP} (cents), null for {@code UNLIMITED}. */
    @Column(name = "amount_cents")
    private Long amountCents;

    /** {@code null} = no expiry (stays until revoked). */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = false, length = 1000)
    private String reason;

    /** The granting staffer (nulls out via {@code ON DELETE SET NULL} if their account is removed). */
    @Column(name = "granted_by_user_id")
    private UUID grantedByUserId;

    /** Snapshot of the granting staffer's name so attribution survives their account deletion. */
    @Column(name = "granted_by_name", length = 255)
    private String grantedByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** Whether this override is currently in effect (no expiry, or expiry in the future relative to {@code now}). */
    public boolean isActiveAt(Instant now) {
        return expiresAt == null || expiresAt.isAfter(now);
    }
}
