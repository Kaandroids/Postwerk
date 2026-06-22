package com.postwerk.exception;

import lombok.Getter;

/**
 * Thrown when a user exceeds a plan-based resource quota (email accounts, automations, AI cost).
 * Handled globally by {@link GlobalExceptionHandler} to return HTTP 429.
 *
 * @since 1.0
 */
@Getter
public class QuotaExceededException extends RuntimeException {

    private final String limitType;
    private final long currentUsage;
    private final long maxAllowed;
    private final String planName;

    public QuotaExceededException(String limitType, long currentUsage, long maxAllowed, String planName) {
        super(limitType + " quota exceeded: " + currentUsage + "/" + maxAllowed + " (plan: " + planName + ")");
        this.limitType = limitType;
        this.currentUsage = currentUsage;
        this.maxAllowed = maxAllowed;
        this.planName = planName;
    }
}
