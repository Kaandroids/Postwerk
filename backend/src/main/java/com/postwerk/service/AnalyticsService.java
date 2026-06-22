package com.postwerk.service;

import com.postwerk.dto.analytics.AnalyticsOverviewResponse;
import com.postwerk.dto.analytics.AutomationAnalyticsResponse;

import java.util.UUID;

/**
 * Org-scoped analytics for the user dashboard Analytics page (#analytics): aggregated automation
 * performance, AI cost, and approval throughput over a {@code 7d / 30d / 90d} window. Read-only;
 * derived from live execution traces, AI token usage, and pending actions. Simulation (TESTING)
 * traces are excluded so metrics reflect real production behavior.
 *
 * @since 1.0
 */
public interface AnalyticsService {

    /** Org-wide overview (Screen 1) for the given range. */
    AnalyticsOverviewResponse getOverview(UUID organizationId, String range);

    /** Per-automation drill-down (Screen 2) for the given range. */
    AutomationAnalyticsResponse getAutomationDetail(UUID organizationId, UUID automationId, String range);
}
