package com.postwerk.dto.admin;

/**
 * Per-relay-cluster (IMAP host) summary for the Email Health "by-cluster" row.
 *
 * <p>{@code status}: {@code down} if ≥2 failing or ≥3 bad; else {@code warn} if any bad; else
 * {@code ok}. {@code bad} = failing + auth errors (anything not healthy and not paused).</p>
 */
public record EmailClusterSummaryResponse(
        String host,
        long healthy,
        long total,
        long failing,
        long bad,
        String status   // ok | warn | down
) {}
