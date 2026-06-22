package com.postwerk.dto.admin;

import java.time.Instant;

/**
 * One entry in a DSAR's status-change history. {@code actor} is a staff display name or "system".
 *
 * @since 1.0
 */
public record DataRequestTimelineEntry(
        String label,
        String actor,
        Instant at
) {}
