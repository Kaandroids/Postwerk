package com.postwerk.dto.admin;

import java.time.Instant;

/**
 * One recurring platform job in the admin Background Jobs list. Schedule is real (cron/interval);
 * last-run/duration/counts are derived from the {@code job_runs} history; {@code nextRunAt} is
 * derived from the schedule. {@code status}: {@code healthy} | {@code failing} | {@code paused}.
 */
public record JobResponse(
        String id,
        String name,
        String type,            // Scheduler | Worker | Maintenance
        String scheduleHuman,
        String status,
        Instant lastRunAt,
        Boolean lastRunOk,
        Long lastDurationMs,
        Instant nextRunAt,      // null = event-driven / paused
        Integer itemsLastRun,   // null = not tracked
        long runsLast24h,
        long failedLast24h,
        String description,
        String drainsQueueId    // null unless the job drains a work queue
) {}
