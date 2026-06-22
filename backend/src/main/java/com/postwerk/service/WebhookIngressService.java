package com.postwerk.service;

import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Service handling inbound webhook requests for automation triggers.
 *
 * <p>Resolves the endpoint by token, verifies authentication (NONE / API_KEY / HMAC),
 * maps the request body into {@code trigger.*} variables, and runs the linked automation
 * synchronously when it is active.</p>
 *
 * @since 1.0
 */
public interface WebhookIngressService {

    /**
     * Processes an inbound webhook request.
     *
     * @param token        the unguessable URL token identifying the endpoint
     * @param rawBody      the raw request body (used for HMAC verification and {@code trigger.body})
     * @param headerLookup case-insensitive header accessor (name → value or null)
     * @return the result, carrying an execution id when the automation ran
     */
    IngestResult ingest(String token, String rawBody, UnaryOperator<String> headerLookup);

    /** Outcome of an ingest call. {@code accepted=false} means the request was ignored (automation not active). */
    record IngestResult(boolean accepted, UUID executionId) {
        public static IngestResult ignored() {
            return new IngestResult(false, null);
        }

        public static IngestResult accepted(UUID executionId) {
            return new IngestResult(true, executionId);
        }
    }
}
