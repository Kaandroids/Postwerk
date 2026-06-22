package com.postwerk.dto.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An announcement row for the admin console. {@code status} is the derived display status
 * (DRAFT / SCHEDULED / LIVE / EXPIRED / ARCHIVED).
 *
 * @since 1.0
 */
public record AdminAnnouncementResponse(
        UUID id,
        String titleDe,
        String titleEn,
        String bodyDe,
        String bodyEn,
        String ctaLabelDe,
        String ctaLabelEn,
        String ctaUrl,
        String type,
        String placement,
        String audience,
        List<String> audiencePlans,
        UUID audienceOrgId,
        String audienceOrgName,
        boolean dismissible,
        String lifecycle,
        String status,
        Instant startsAt,
        Instant endsAt,
        String createdByName,
        String updatedByName,
        Instant updatedAt
) {}
