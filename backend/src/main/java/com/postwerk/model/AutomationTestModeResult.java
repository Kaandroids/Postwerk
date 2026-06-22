package com.postwerk.model;

import com.postwerk.model.enums.TestResultFeedback;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "automation_test_mode_results")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutomationTestModeResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "automation_id", nullable = false)
    private UUID automationId;

    @Column(name = "email_id", nullable = false)
    private UUID emailId;

    @Column(name = "trace_id")
    private UUID traceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "simulated_actions", nullable = false, columnDefinition = "jsonb")
    private String simulatedActions;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback", length = 20)
    private TestResultFeedback feedback;

    @Column(name = "feedback_note", columnDefinition = "TEXT")
    private String feedbackNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "feedback_at")
    private Instant feedbackAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
