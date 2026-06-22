package com.postwerk.dto.admin;

import java.time.Instant;

/** Current platform maintenance-mode state (admin System Health). */
public record MaintenanceModeResponse(
        boolean enabled,
        String message,
        Instant updatedAt
) {}
