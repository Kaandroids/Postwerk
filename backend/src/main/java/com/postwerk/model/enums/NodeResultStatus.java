package com.postwerk.model.enums;

/**
 * Enumeration of granular result statuses for individual node execution within a trace.
 *
 * <p>Covers filter outcomes ({@code PASSED}, {@code MATCHED}, {@code NOT_MATCHED}),
 * AI operations ({@code CATEGORIZED}, {@code EXTRACTED}), action results ({@code EXECUTED}),
 * supervised-mode parking ({@code PENDING_APPROVAL}), and error/skip states
 * ({@code SKIPPED}, {@code ERROR}).</p>
 *
 * @since 1.0
 */
public enum NodeResultStatus {
    PASSED,
    MATCHED,
    NOT_MATCHED,
    CATEGORIZED,
    EXTRACTED,
    EXECUTED,
    SIMULATED,
    /** Side effect was resolved but parked for human approval (supervised mode); not performed. */
    PENDING_APPROVAL,
    SKIPPED,
    ERROR
}
