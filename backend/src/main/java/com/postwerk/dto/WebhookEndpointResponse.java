package com.postwerk.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response describing an inbound webhook endpoint for the automation editor.
 * The signing secret is never returned; only {@code hasSecret} indicates whether one is set.
 */
public record WebhookEndpointResponse(
        UUID id,
        UUID automationId,
        UUID nodeId,
        String token,
        String url,
        String authMode,
        String authHeaderName,
        boolean hasSecret,
        boolean active,
        long triggerCount,
        Instant lastTriggeredAt
) {}
