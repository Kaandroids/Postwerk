package com.postwerk.dto.admin;

/** Toggle (and optionally annotate) platform maintenance mode. */
public record MaintenanceModeRequest(
        boolean enabled,
        String message
) {}
