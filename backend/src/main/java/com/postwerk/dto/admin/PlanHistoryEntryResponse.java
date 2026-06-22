package com.postwerk.dto.admin;

import java.time.Instant;

/**
 * One plan-assignment change in a subscription's history timeline. Derived from the audit log
 * ({@code ORG_PLAN_CHANGED} entries) — so history is forward-looking from when the feature shipped.
 */
public record PlanHistoryEntryResponse(
        Instant at,
        String detail,
        String by
) {}
