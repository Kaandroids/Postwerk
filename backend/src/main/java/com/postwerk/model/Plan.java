package com.postwerk.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a subscription plan with resource quotas and feature flags.
 * Defines limits for email accounts, automations, AI cost, and webhook access.
 *
 * @since 1.0
 */
@Entity
@Table(name = "plans")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Plan {

    public static final String DEFAULT_PLAN_NAME = "STARTER";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "token_limit", nullable = false)
    private int tokenLimit;

    @Column(name = "automation_limit", nullable = false)
    private int automationLimit;

    @Column(name = "email_account_limit", nullable = false)
    private int emailAccountLimit;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "api_webhook_enabled", nullable = false)
    private boolean apiWebhookEnabled;

    @Column(name = "cost_limit_cents", nullable = false)
    private int costLimitCents;

    @Column(name = "inbound_webhook_limit", nullable = false)
    private int inboundWebhookLimit;

    @Column(name = "marketplace_publish_enabled", nullable = false)
    @Builder.Default
    private boolean marketplacePublishEnabled = true;

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
