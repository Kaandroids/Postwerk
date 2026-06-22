package com.postwerk.model;

import com.postwerk.model.enums.Role;
import com.postwerk.model.enums.StaffRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a registered application user.
 *
 * <p>Stores authentication credentials (BCrypt-hashed password), profile information,
 * GDPR consent timestamps (privacy policy, terms, marketing opt-in), and login tracking.
 * Soft-deleted via {@code deletedAt} column with an optional deletion reason.</p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String company;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.USER;

    /** Platform-staff role (admin panel). {@code null} = regular customer, not staff. */
    @Enumerated(EnumType.STRING)
    @Column(name = "staff_role", length = 20)
    private StaffRole staffRole;

    /** When the current staff role was first granted (for the Staff &amp; Roles roster); null if not staff. */
    @Column(name = "staff_role_since")
    private Instant staffRoleSince;

    @Column(name = "marketing_opt_in", nullable = false)
    private boolean marketingOptIn;

    @Column(name = "privacy_accepted_at")
    private Instant privacyAcceptedAt;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    @Column(name = "privacy_version", length = 20)
    private String privacyVersion;

    @Column(name = "marketing_opted_in_at")
    private Instant marketingOptedInAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deletion_reason", length = 50)
    private String deletionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private Plan plan;

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
