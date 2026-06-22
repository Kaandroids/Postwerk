package com.postwerk.dto.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Detailed organization view for platform staff: identity, owner, plan, members and usage aggregates. */
public record AdminOrgDetailResponse(
        UUID id,
        String name,
        String slug,
        boolean personal,
        UUID ownerUserId,
        String ownerEmail,
        String ownerName,
        UUID planId,
        String planName,
        long memberCount,
        long mailboxCount,
        long automationCount,
        long aiCostMicrosThisMonth,
        Instant createdAt,
        Instant suspendedAt,
        String suspensionReason,
        List<AdminOrgMemberResponse> members
) {}
