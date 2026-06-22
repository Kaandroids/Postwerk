package com.postwerk.service;

import com.postwerk.dto.admin.BackgroundJobsKpisResponse;
import com.postwerk.dto.admin.JobDetailResponse;
import com.postwerk.dto.admin.JobQueueResponse;
import com.postwerk.dto.admin.JobResponse;

import java.util.List;
import java.util.UUID;

/**
 * Platform-staff Background Jobs: the platform's recurring scheduled jobs (real schedules + run
 * history from {@code job_runs}) and the work queues they drain, with gated run-now / pause / resume.
 * Reads need {@code INFRA_VIEW}; mutations need {@code INFRA_MANAGE} (enforced at the controller).
 *
 * @since 1.0
 */
public interface AdminBackgroundJobsService {

    List<JobResponse> listJobs();

    BackgroundJobsKpisResponse kpis();

    List<JobQueueResponse> queues();

    JobDetailResponse getJob(String id);

    /** Triggers a manual run of the job in the background; returns its current row. */
    JobResponse runNow(String id, UUID actorUserId, String ip);

    JobResponse setPaused(String id, boolean paused, UUID actorUserId, String ip);
}
