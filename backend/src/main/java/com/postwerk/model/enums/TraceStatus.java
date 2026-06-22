package com.postwerk.model.enums;

/**
 * Enumeration of overall status values for an email automation trace.
 *
 * <p>{@code RUNNING} while the automation is processing, {@code SUCCESS}
 * when all nodes complete without error, and {@code FAILED} on any node failure.</p>
 *
 * @since 1.0
 */
public enum TraceStatus {
    RUNNING,
    SUCCESS,
    FAILED
}
