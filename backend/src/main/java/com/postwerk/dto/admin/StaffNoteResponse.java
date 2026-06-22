package com.postwerk.dto.admin;

import java.time.Instant;
import java.util.UUID;

/** A single internal staff note about a user (admin "Users support tooling"). */
public record StaffNoteResponse(
        UUID id,
        String body,
        String authorName,
        String authorEmail,
        Instant createdAt
) {}
