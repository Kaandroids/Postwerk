package com.postwerk.model.enums;

/**
 * Enumeration of lifecycle states for an automation workflow.
 *
 * <p>{@code ACTIVE} automations are evaluated during email sync;
 * {@code PAUSED} automations are skipped until reactivated.</p>
 *
 * @since 1.0
 */
public enum AutomationStatus {
    ACTIVE,
    TESTING,
    PAUSED
}
