package com.postwerk.model.enums;

/**
 * Lifecycle state of a data-subject access request (DSAR).
 *
 * @since 1.0
 */
public enum DataRequestStatus {
    /** Received, awaiting handler / verification. */
    PENDING,
    /** Being worked (export building, erasure executed, etc.). */
    IN_PROGRESS,
    /** Resolved and closed. */
    COMPLETED,
    /** Closed without fulfilment (e.g. identity could not be verified). */
    REJECTED
}
