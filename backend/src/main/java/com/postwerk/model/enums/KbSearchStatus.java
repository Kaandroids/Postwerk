package com.postwerk.model.enums;

/**
 * Outcome of a knowledge-base search performed by the {@code VECTOR_SEARCH} node.
 *
 * <p>Distinct from the routing handle: both {@code NOT_MATCHED} and {@code ERROR} route the node's
 * {@code fail} handle, but they are kept apart so a trace can tell a genuine "no good match" (a
 * business outcome) from an execution failure (Gemini down, KB missing). {@code MATCHED} routes
 * {@code success}.</p>
 *
 * @since 1.0
 */
public enum KbSearchStatus {
    /** A candidate matched at or above the confidence threshold. */
    MATCHED,
    /** No candidate matched, or confidence fell below the threshold, or the KB had no candidates. */
    NOT_MATCHED,
    /** The search could not complete (embedding/judge call failed, KB not found, etc.). */
    ERROR
}
