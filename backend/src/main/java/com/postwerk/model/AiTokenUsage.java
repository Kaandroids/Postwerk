package com.postwerk.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity tracking AI model token consumption per user.
 *
 * <p>Records the model used, operation type (classify, extract, embed, chat),
 * and token counts (prompt, output, total) plus billable characters.
 * Used for usage monitoring, billing, and rate-limiting decisions.</p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "ai_token_usage")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTokenUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Billing organization (#4). The monthly AI cost cap is enforced per-organization.
     * Nullable: the row is written on the async usage path and must never be lost if the
     * billing org could not be resolved (historical rows predate org scoping).
     */
    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(nullable = false, length = 50)
    private String model;

    @Column(nullable = false, length = 30)
    private String operation;

    @Column(name = "prompt_tokens")
    private int promptTokens;

    @Column(name = "output_tokens")
    private int outputTokens;

    @Column(name = "total_tokens")
    private int totalTokens;

    @Column(name = "billable_chars")
    private int billableChars;

    @Column(name = "cost_micros", nullable = false)
    private int costMicros;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
