package com.postwerk.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a test case for an automation.
 * Each test case defines a synthetic email input and optional assertions
 * that validate the automation's node graph behavior during a dry-run execution.
 */
@Entity
@Table(name = "automation_test_cases")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutomationTestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "automation_id", nullable = false)
    private UUID automationId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "email_input", columnDefinition = "JSONB", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String emailInput;

    @Column(columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String assertions;

    /**
     * Per-node mock configuration, keyed by node id. Each value is a serialized
     * {@code NodeMock} declaring MOCK (synthesize response / force error) or LIVE
     * (perform the real call). Null when the test case uses default dry-run simulation.
     */
    @Column(columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String mocks;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
