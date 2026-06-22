package com.postwerk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request payload for sending a message to the AI assistant.
 *
 * @param message        the user's chat message
 * @param conversationId optional conversation ID to continue an existing chat
 * @param model          optional model override
 * @param language       optional UI language code (e.g. {@code "de"}, {@code "en"}) used to pin the
 *                       assistant's reply language — avoids mis-detection on very short messages
 *                       (e.g. a one-word "Build automation" button press)
 */
public record AiChatRequest(
        @NotBlank(message = "Message is required")
        @Size(max = 10000, message = "Message cannot exceed 10000 characters")
        String message,
        UUID conversationId,
        String model,
        String language
) {}
