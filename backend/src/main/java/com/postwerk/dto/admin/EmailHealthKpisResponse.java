package com.postwerk.dto.admin;

/**
 * KPI totals for the Email Health header strip, computed across ALL non-deleted mailboxes.
 *
 * <p>{@code avgSyncLagMinutes} is the mean sync age over healthy, previously-synced mailboxes
 * (null when none have synced). {@code authErrors} is a subset of {@code failing}'s siblings — it
 * counts mailboxes whose last status was specifically a credentials rejection.</p>
 */
public record EmailHealthKpisResponse(
        long total,
        long healthy,
        long failing,
        long authErrors,
        long paused,
        Long avgSyncLagMinutes
) {}
