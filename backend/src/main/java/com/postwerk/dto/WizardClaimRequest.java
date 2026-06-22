package com.postwerk.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request payload for claiming a wizard session after registration.
 *
 * @param sessionId The wizard session ID to claim
 */
public record WizardClaimRequest(
        @NotNull UUID sessionId
) {}
