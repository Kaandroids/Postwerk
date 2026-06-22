package com.postwerk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request payload for the wizard chat SSE endpoint.
 *
 * @param sessionId Existing session ID to continue, or null for a new session
 * @param message   The user's message
 * @param lang      Language code (de or en)
 */
public record WizardChatRequest(
        UUID sessionId,
        @NotBlank @Size(max = 2000) String message,
        @NotBlank @Size(max = 5) String lang
) {}
