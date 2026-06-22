package com.postwerk.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One execution of a recurring platform job (admin Background Jobs). Recorded best-effort by
 * {@link com.postwerk.service.JobRunService} around the real {@code @Scheduled} methods.
 *
 * @since 1.0
 */
@Entity
@Table(name = "job_runs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Registry id of the job, e.g. {@code email-sync}. */
    @Column(name = "job_id", nullable = false, length = 64)
    private String jobId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    @Column(nullable = false)
    private boolean ok;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "items_processed")
    private Integer itemsProcessed;

    @Column(length = 1000)
    private String message;

    /** {@code schedule} (cron/interval tick) or {@code manual} (staff "run now"). */
    @Column(name = "triggered_by", nullable = false, length = 16)
    private String triggeredBy;

    @Column(name = "actor_user_id")
    private UUID actorUserId;
}
