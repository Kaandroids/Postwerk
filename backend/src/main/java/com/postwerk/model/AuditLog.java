package com.postwerk.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing an audit trail entry.
 *
 * <p>Records user and system actions for compliance and debugging purposes.
 * Each entry captures the action type ({@link AuditAction}), a free-form detail string,
 * the acting user, and the client IP address. Entries are append-only (no updates or deletes).</p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "audit_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    /** Organization the action was performed in. Null for system jobs and pre-org actions (login/registration). */
    @Column(name = "organization_id")
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditAction action;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
