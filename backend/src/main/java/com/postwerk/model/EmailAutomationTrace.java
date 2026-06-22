package com.postwerk.model;

import com.postwerk.model.enums.TraceStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity recording the execution trace of an automation against a specific email.
 *
 * <p>Captures the automation's identity, start/completion timestamps, overall status,
 * and an ordered list of {@link EmailNodeTrace} entries for each node visited.
 * Used for debugging, auditing, and displaying execution history in the UI.</p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "email_automation_traces")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailAutomationTrace {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_id")
    private Email email;

    @Column(name = "automation_id", nullable = false)
    private UUID automationId;

    @Column(name = "automation_execution_id")
    private UUID automationExecutionId;

    @Column(name = "automation_name", nullable = false)
    private String automationName;

    @Column(name = "automation_color", length = 20)
    private String automationColor;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TraceStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** True when this trace was produced by a TESTING (Simulationsmodus) dry-run, not a live execution. */
    @Column(nullable = false)
    private boolean simulation;

    @OneToMany(mappedBy = "trace", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("executionOrder ASC")
    @BatchSize(size = 50)
    @Builder.Default
    private List<EmailNodeTrace> nodeTraces = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) startedAt = Instant.now();
        if (status == null) status = TraceStatus.RUNNING;
    }
}
