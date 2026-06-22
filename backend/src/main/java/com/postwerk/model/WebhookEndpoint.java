package com.postwerk.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing an inbound webhook receiver endpoint.
 *
 * <p>Maps a publicly reachable, unguessable URL ({@code /api/v1/hooks/{token}}) to a specific
 * TRIGGER node of an automation. When an external system POSTs to the URL, the automation runs
 * and the request body is mapped into {@code trigger.*} variables. Three auth modes are supported
 * (NONE, API_KEY, HMAC); the shared {@code signingSecret} is AES-256-GCM encrypted at rest.</p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "webhook_endpoints")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Owning organization — the scoping key in the multi-tenant model (#4). */
    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "automation_id", nullable = false)
    private UUID automationId;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Column(nullable = false, length = 64, unique = true)
    private String token;

    @Column(name = "auth_mode", nullable = false, length = 16)
    @Builder.Default
    private String authMode = "NONE";

    @Column(name = "auth_header_name", length = 64)
    private String authHeaderName;

    @Column(name = "signing_secret")
    private String signingSecret;

    @Column(name = "parameter_set_id")
    private UUID parameterSetId;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "trigger_count", nullable = false)
    @Builder.Default
    private long triggerCount = 0;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

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
