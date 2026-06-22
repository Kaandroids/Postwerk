package com.postwerk.model.enums;

/**
 * Discriminates an automation's role.
 *
 * <ul>
 *   <li>{@code AUTOMATION} — a normal event/trigger-driven workflow (the default).</li>
 *   <li>{@code INTEGRATION} — a trigger-less, parametrized reusable flow invoked from inside an
 *       automation via an {@code INTEGRATION_CALL} node. Behaves like a function:
 *       INPUT (parameter set) → internal flow → OUTPUT (0 or 1 parameter set).</li>
 * </ul>
 *
 * @since 1.0
 */
public enum AutomationKind {
    AUTOMATION,
    INTEGRATION
}
