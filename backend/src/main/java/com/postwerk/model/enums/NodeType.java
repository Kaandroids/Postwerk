package com.postwerk.model.enums;

import java.util.Set;

/**
 * Enumeration of automation workflow node types.
 *
 * @since 1.0
 */
public enum NodeType {
    TRIGGER,
    FILTER,
    EXTRACT,
    CATEGORIZE,
    DELAY,
    LABEL,
    EMAIL_ACTION,
    REMOVE_LABEL,
    WEBHOOK,
    SEND_EMAIL,
    INPUT,
    OUTPUT,
    INTEGRATION_CALL,
    VECTOR_SEARCH,
    NOTIFY;

    /**
     * Node types that perform an external/observable side effect (send/forward/move email, label,
     * outbound call, integration, in-app/email notification). These are the nodes eligible for
     * supervised execution modes (AUTO / REVIEW / OFF) and the ones surfaced as "simulated actions"
     * in test mode.
     */
    public static final Set<NodeType> ACTION_TYPES = Set.of(
            EMAIL_ACTION, SEND_EMAIL, WEBHOOK, LABEL, REMOVE_LABEL, INTEGRATION_CALL, NOTIFY);

    /** Whether this node performs a side effect (see {@link #ACTION_TYPES}). */
    public boolean isAction() {
        return ACTION_TYPES.contains(this);
    }
}
