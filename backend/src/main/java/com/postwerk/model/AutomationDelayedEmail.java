package com.postwerk.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing an email queued for deferred automation processing after a DELAY node.
 *
 * @since 1.0
 */
@Entity
@Table(name = "automation_delayed_emails")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutomationDelayedEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "email_id", nullable = false)
    private UUID emailId;

    @Column(name = "automation_id", nullable = false)
    private UUID automationId;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Column(name = "delayed_until", nullable = false)
    private Instant delayedUntil;

    @Column(nullable = false)
    private boolean processed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
