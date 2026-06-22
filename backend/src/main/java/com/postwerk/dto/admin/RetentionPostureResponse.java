package com.postwerk.dto.admin;

import java.time.Instant;

/**
 * The standing automated retention policy (real {@code GdprProperties}) plus the last run of the
 * nightly {@code data-retention} sweep. {@code lastSweepAt} is null if the sweep has never run.
 *
 * @since 1.0
 */
public record RetentionPostureResponse(
        int emailDays,
        int conversationDays,
        int ipDays,
        int auditDays,
        Instant lastSweepAt
) {}
