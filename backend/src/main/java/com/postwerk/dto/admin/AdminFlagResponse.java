package com.postwerk.dto.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A feature-flag row for the admin console. {@code status} is the derived display status
 * (ON / ROLLING / OFF / KILLED / ARCHIVED); {@code stale} flags long-100%-on cleanup candidates.
 *
 * @since 1.0
 */
public record AdminFlagResponse(
        UUID id,
        String key,
        String name,
        String description,
        String kind,
        boolean enabled,
        int rollout,
        String audience,
        List<String> audiencePlans,
        UUID audienceOrgId,
        String audienceOrgName,
        List<FlagOverrideDto> overrides,
        boolean killed,
        boolean archived,
        boolean stale,
        String status,
        String updatedByName,
        Instant updatedAt
) {}
