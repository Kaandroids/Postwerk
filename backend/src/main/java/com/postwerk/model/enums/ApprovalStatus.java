package com.postwerk.model.enums;

/**
 * Lifecycle of a supervised-mode {@code PendingAction}: a side effect that was resolved during a
 * live automation run but parked for human review instead of being performed.
 *
 * @since 1.0
 */
public enum ApprovalStatus {
    /** Awaiting a human decision. */
    PENDING,
    /** Approved by a user — the parked side effect has been (or is being) performed. */
    APPROVED,
    /** Rejected by a user — the side effect will never be performed. */
    REJECTED,
    /** Expired before any decision (e.g. retention cutoff) — never performed. */
    EXPIRED
}
