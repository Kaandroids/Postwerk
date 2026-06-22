package com.postwerk.controller;

import com.postwerk.dto.automation.PendingActionResponse;
import com.postwerk.model.enums.ApprovalStatus;
import com.postwerk.model.enums.Permission;
import com.postwerk.service.OrgContext;
import com.postwerk.service.OrgContextService;
import com.postwerk.service.PendingActionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Approval inbox for supervised-mode actions (#3a): side effects parked for human review during a
 * live automation run. Scoped to the active organization (#4): viewing requires APPROVAL_VIEW;
 * approving / rejecting / reclassifying (which perform or correct real side effects) require
 * APPROVAL_DECIDE.
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/pending-actions")
public class PendingActionController {

    private final PendingActionService pendingActionService;
    private final OrgContextService orgContext;

    public PendingActionController(PendingActionService pendingActionService,
                                   OrgContextService orgContext) {
        this.pendingActionService = pendingActionService;
        this.orgContext = orgContext;
    }

    @GetMapping
    public Page<PendingActionResponse> list(OrgContext ctx,
                                            @RequestParam(required = false) ApprovalStatus status,
                                            @PageableDefault(size = 20) Pageable pageable) {
        orgContext.require(ctx, Permission.APPROVAL_VIEW);
        return pendingActionService.list(ctx.organizationId(), status, pageable);
    }

    @GetMapping("/count")
    public Map<String, Long> pendingCount(OrgContext ctx) {
        orgContext.require(ctx, Permission.APPROVAL_VIEW);
        return Map.of("pending", pendingActionService.countPending(ctx.organizationId()));
    }

    @PostMapping("/{id}/approve")
    public PendingActionResponse approve(OrgContext ctx,
                                         @PathVariable UUID id) {
        orgContext.require(ctx, Permission.APPROVAL_DECIDE);
        return pendingActionService.approve(ctx.organizationId(), ctx.userId(), id);
    }

    @PostMapping("/{id}/reject")
    public PendingActionResponse reject(OrgContext ctx,
                                        @PathVariable UUID id,
                                        @RequestParam(required = false) String note) {
        orgContext.require(ctx, Permission.APPROVAL_DECIDE);
        return pendingActionService.reject(ctx.organizationId(), ctx.userId(), id, note);
    }

    /** #3c: teach the correct category from this action's email and reject the (wrongly-triggered) action. */
    @PostMapping("/{id}/reclassify")
    public PendingActionResponse reclassify(OrgContext ctx,
                                            @PathVariable UUID id,
                                            @RequestParam UUID categoryId) {
        orgContext.require(ctx, Permission.APPROVAL_DECIDE);
        return pendingActionService.reclassify(ctx.organizationId(), ctx.userId(), id, categoryId);
    }
}
