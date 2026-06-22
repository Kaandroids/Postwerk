package com.postwerk.dto.admin;

/** KPI strip totals for the admin Background Jobs header. */
public record BackgroundJobsKpisResponse(
        long scheduled,
        long runs24h,
        long failed24h,
        long queueDepth,
        Long avgDurationMs,
        Long nextRunMinutes,
        long paused,
        long failing
) {}
