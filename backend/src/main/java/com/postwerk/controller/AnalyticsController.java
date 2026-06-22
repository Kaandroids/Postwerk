package com.postwerk.controller;

import com.postwerk.dto.analytics.AnalyticsOverviewResponse;
import com.postwerk.dto.analytics.AutomationAnalyticsResponse;
import com.postwerk.model.enums.Permission;
import com.postwerk.service.AnalyticsService;
import com.postwerk.service.OrgContext;
import com.postwerk.service.OrgContextService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * User-facing analytics (#analytics): org-wide automation performance + AI cost overview and a
 * per-automation drill-down. Scoped to the active organization (#4); requires AUTOMATION_VIEW.
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final OrgContextService orgContext;

    public AnalyticsController(AnalyticsService analyticsService, OrgContextService orgContext) {
        this.analyticsService = analyticsService;
        this.orgContext = orgContext;
    }

    /** Org-wide overview for the given window (7d/30d/90d; defaults to 30d). */
    @GetMapping("/overview")
    public AnalyticsOverviewResponse overview(OrgContext ctx,
                                              @RequestParam(defaultValue = "30d") String range) {
        orgContext.require(ctx, Permission.AUTOMATION_VIEW);
        return analyticsService.getOverview(ctx.organizationId(), range);
    }

    /** Per-automation drill-down for the given window. */
    @GetMapping("/automations/{id}")
    public AutomationAnalyticsResponse automationDetail(OrgContext ctx,
                                                        @PathVariable UUID id,
                                                        @RequestParam(defaultValue = "30d") String range) {
        orgContext.require(ctx, Permission.AUTOMATION_VIEW);
        return analyticsService.getAutomationDetail(ctx.organizationId(), id, range);
    }
}
