package com.postwerk.dto.admin;

import java.time.Instant;

/**
 * KPI roll-up for the Announcements console — lifecycle counts only (no impression/view tracking).
 * {@code nextLiveAt} is the soonest scheduled go-live, or null if none scheduled.
 *
 * @since 1.0
 */
public record AnnouncementKpisResponse(
        long live,
        long scheduled,
        long drafts,
        long maintenanceLive,
        long expired30d,
        Instant nextLiveAt
) {}
