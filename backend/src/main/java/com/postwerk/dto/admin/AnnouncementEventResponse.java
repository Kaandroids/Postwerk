package com.postwerk.dto.admin;

import java.time.Instant;

/** One change-history entry (label + actor + timestamp) for an announcement or feature flag. */
public record AnnouncementEventResponse(
        String label,
        String actor,
        Instant at
) {}
