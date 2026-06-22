package com.postwerk.controller;

import com.postwerk.dto.automation.ActivityEntry;
import com.postwerk.model.enums.Permission;
import com.postwerk.service.ActivityService;
import com.postwerk.service.OrgContext;
import com.postwerk.service.OrgContextService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Production activity feed (#3d): what the organization's automations did to incoming email, with
 * per-step results and AI reasoning, surfaced from execution traces. Scoped to the active
 * organization (#4); requires AUDIT_VIEW.
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/activity")
public class ActivityController {

    private final ActivityService activityService;
    private final OrgContextService orgContext;

    public ActivityController(ActivityService activityService, OrgContextService orgContext) {
        this.activityService = activityService;
        this.orgContext = orgContext;
    }

    @GetMapping
    public Page<ActivityEntry> recent(OrgContext ctx,
                                      @PageableDefault(size = 20) Pageable pageable) {
        orgContext.require(ctx, Permission.AUDIT_VIEW);
        return activityService.getRecent(ctx.organizationId(), pageable);
    }
}
