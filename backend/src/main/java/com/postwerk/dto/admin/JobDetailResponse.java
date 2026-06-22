package com.postwerk.dto.admin;

import java.util.List;

/** A job plus its recent-runs timeline (admin Background Jobs detail modal). */
public record JobDetailResponse(
        JobResponse job,
        List<JobRunResponse> recentRuns
) {}
