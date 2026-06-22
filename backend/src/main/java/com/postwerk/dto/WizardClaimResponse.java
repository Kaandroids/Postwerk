package com.postwerk.dto;

import java.util.UUID;

/**
 * Response payload after successfully claiming a wizard session.
 *
 * @param automationId The created automation's ID
 */
public record WizardClaimResponse(
        UUID automationId
) {}
