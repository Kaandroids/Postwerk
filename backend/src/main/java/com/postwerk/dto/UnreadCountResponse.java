package com.postwerk.dto;

/**
 * Lightweight payload for the unread-count polling endpoint.
 *
 * @since 1.0
 */
public record UnreadCountResponse(long count) {
}
