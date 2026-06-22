package com.postwerk.model;

import com.postwerk.model.enums.ExecutionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity recording a single execution run of an {@link Automation}.
 *
 * <p>Tracks when the automation was triggered, when it completed, how many emails
 * were processed, and whether it succeeded or failed. Error details are stored
 * in the {@code errorLog} text column.</p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "automation_executions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutomationExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "automation_id", nullable = false)
    private UUID automationId;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExecutionStatus status;

    @Column(name = "processed_count", nullable = false)
    private int processedCount;

    @Column(name = "error_log", columnDefinition = "TEXT")
    private String errorLog;

    @PrePersist
    protected void onCreate() {
        triggeredAt = Instant.now();
        if (status == null) status = ExecutionStatus.RUNNING;
    }
}
