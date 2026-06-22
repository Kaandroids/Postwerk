package com.postwerk.model;

/**
 * Represents the current phase of an AI conversation regarding automation creation.
 *
 * <p>Controls tool availability: during PLANNING, automation write tools are blocked.
 * Only after user confirmation (BUILDING) can the AI create/modify automations.</p>
 */
public enum ConversationPhase {
    /** Default — no automation request yet, all tools available. */
    OPEN,
    /** AI proposed an automation plan, waiting for user confirmation — automation write tools blocked. */
    PLANNING,
    /** User confirmed the plan — all tools available for building. */
    BUILDING
}
