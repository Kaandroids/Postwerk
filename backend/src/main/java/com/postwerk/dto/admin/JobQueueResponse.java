package com.postwerk.dto.admin;

import java.util.List;

/**
 * A work queue summary (admin Background Jobs). {@code tone}: {@code backlog} (>100 pending) |
 * {@code clear}. {@code drainJobId} is the job that drains it (clicking opens that job's detail).
 */
public record JobQueueResponse(
        String id,
        String name,
        String drainJobId,
        long pending,
        String tone,
        List<Breakdown> breakdown
) {
    /** A labelled count within a queue (e.g. pending / approved / rejected). {@code dot}: ok|warn|danger. */
    public record Breakdown(String label, long value, String dot) {}
}
