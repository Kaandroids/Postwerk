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
 * JPA entity representing the result of a single automation test case execution.
 * Stores the execution status (PASSED/FAILED/ERROR), per-node trace results,
 * assertion outcomes, duration, and any error message from the run.
 */
@Entity
@Table(name = "automation_test_results")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutomationTestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "test_case_id", nullable = false)
    private UUID testCaseId;

    @Column(name = "automation_id", nullable = false)
    private UUID automationId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "node_results", columnDefinition = "JSONB", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String nodeResults;

    @Column(name = "assertion_results", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String assertionResults;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @PrePersist
    protected void onCreate() {
        if (executedAt == null) executedAt = Instant.now();
    }
}
