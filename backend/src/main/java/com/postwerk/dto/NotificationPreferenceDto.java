package com.postwerk.dto;

/**
 * One row of the notification preference matrix: a category and its per-channel toggles. Used by both
 * {@code GET} (full matrix, defaults filled) and {@code PUT} (upsert) of {@code /notifications/preferences}.
 *
 * @since 1.0
 */
public record NotificationPreferenceDto(String category,
                                        boolean inApp,
                                        boolean email) {
}
