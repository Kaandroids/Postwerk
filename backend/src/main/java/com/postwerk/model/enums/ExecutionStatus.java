package com.postwerk.model.enums;

/**
 * Enumeration of possible outcomes for an automation execution run.
 *
 * <p>{@code RUNNING} while in progress, transitioning to {@code SUCCESS}
 * on completion or {@code FAILED} if an unrecoverable error occurs.</p>
 *
 * @since 1.0
 */
public enum ExecutionStatus {
    RUNNING,
    SUCCESS,
    FAILED
}
