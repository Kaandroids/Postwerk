package com.postwerk.dto.admin;

import java.time.Instant;
import java.util.UUID;

/** A lightweight automation row for the admin organization-detail "Automations" tab. */
public record AdminAutomationSummaryResponse(
        UUID id,
        String name,
        String status,
        String kind,
        Instant createdAt,
        Instant lastRunAt
) {}
