package com.postwerk.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity holding the per-model AI pricing rates (USD per million tokens) used to compute the
 * cost of each AI call. Admin-editable at runtime; seeded from {@code application.yml}
 * (gemini.pricing) and read through {@link com.postwerk.service.PricingService} with a cache.
 *
 * @since 1.0
 */
@Entity
@Table(name = "model_pricing")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelPricing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Model identifier, e.g. {@code gemini-2.5-pro}. Matches the model name passed to the AI client. */
    @Column(nullable = false, unique = true, length = 100)
    private String model;

    /** USD cost per million input (prompt) tokens. */
    @Column(name = "input_per_million", nullable = false)
    private double inputPerMillion;

    /** USD cost per million output (candidate) tokens. */
    @Column(name = "output_per_million", nullable = false)
    private double outputPerMillion;

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
