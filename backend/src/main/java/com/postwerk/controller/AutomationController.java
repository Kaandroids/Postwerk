package com.postwerk.controller;

import com.postwerk.dto.*;
import com.postwerk.dto.automation.*;
import com.postwerk.model.enums.Permission;
import com.postwerk.service.AutomationService;
import com.postwerk.service.AutomationTestService;
import com.postwerk.service.OrgContext;
import com.postwerk.service.OrgContextService;
import com.postwerk.service.TestModeService;
import com.postwerk.util.IpResolverUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for automation workflow CRUD, execution history, and manual triggers.
 *
 * <p>Automations are org-owned email processing pipelines (a DAG of trigger/categorize/extract/action
 * nodes). Reads require AUTOMATION_VIEW, edits AUTOMATION_EDIT; activating or running an automation
 * (live side effects) requires AUTOMATION_ACTIVATE. The test-case and test-mode sub-resources remain
 * user-scoped pending their own domain flip.</p>
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/automations")
@Tag(name = "Automations", description = "Automation workflow CRUD, flow editor, execution history, and bulk import/export")
public class AutomationController {

    private final AutomationService automationService;
    private final AutomationTestService automationTestService;
    private final TestModeService testModeService;
    private final OrgContextService orgContext;

    public AutomationController(AutomationService automationService,
                                 AutomationTestService automationTestService,
                                 TestModeService testModeService,
                                 OrgContextService orgContext) {
        this.automationService = automationService;
        this.automationTestService = automationTestService;
        this.testModeService = testModeService;
        this.orgContext = orgContext;
    }

    /** Creates a new automation workflow. */
    @PostMapping
    public ResponseEntity<AutomationResponse> create(
            OrgContext ctx,
            @Valid @RequestBody AutomationRequest request,
            HttpServletRequest httpRequest) {
        orgContext.require(ctx, Permission.AUTOMATION_EDIT);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(automationService.create(ctx.organizationId(), ctx.userId(), request, IpResolverUtil.extractIp(httpRequest)));
    }

    /** Lists all automations in the active organization. */
    @GetMapping
    public ResponseEntity<List<AutomationResponse>> list(
            OrgContext ctx) {
        orgContext.require(ctx, Permission.AUTOMATION_VIEW);
        return ResponseEntity.ok(automationService.listByOrg(ctx.organizationId()));
    }

    /** Lists the organization's reusable, trigger-less integrations (for the call-node picker + integrations page). */
    @GetMapping("/integrations")
    public ResponseEntity<List<AutomationResponse>> listIntegrations(
            OrgContext ctx) {
        orgContext.require(ctx, Permission.AUTOMATION_VIEW);
        return ResponseEntity.ok(automationService.listIntegrations(ctx.organizationId()));
    }

    /** Returns a single automation with full node/connection detail. */
    @GetMapping("/{id}")
    public ResponseEntity<AutomationDetailResponse> get(
            OrgContext ctx,
            @PathVariable UUID id) {
        orgContext.require(ctx, Permission.AUTOMATION_VIEW);
        return ResponseEntity.ok(automationService.getById(ctx.organizationId(), id));
    }

    /** Updates an existing automation's metadata. */
    @PutMapping("/{id}")
    public ResponseEntity<AutomationResponse> update(
            OrgContext ctx,
            @PathVariable UUID id,
            @Valid @RequestBody AutomationRequest request,
            HttpServletRequest httpRequest) {
        orgContext.require(ctx, Permission.AUTOMATION_EDIT);
        return ResponseEntity.ok(automationService.update(ctx.organizationId(), ctx.userId(), id, request, IpResolverUtil.extractIp(httpRequest)));
    }

    /** Deletes an automation by ID. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            OrgContext ctx,
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        orgContext.require(ctx, Permission.AUTOMATION_EDIT);
        automationService.delete(ctx.organizationId(), ctx.userId(), id, IpResolverUtil.extractIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    /** Toggles the lock state of an automation. */
    @PatchMapping("/{id}/lock")
    public ResponseEntity<AutomationResponse> toggleLock(
            OrgContext ctx,
            @PathVariable UUID id) {
        orgContext.require(ctx, Permission.AUTOMATION_EDIT);
        return ResponseEntity.ok(automationService.toggleLock(ctx.organizationId(), id));
    }

    /** Toggles the active/inactive status of an automation. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<AutomationResponse> updateStatus(
            OrgContext ctx,
            @PathVariable UUID id,
            @Valid @RequestBody AutomationStatusRequest request,
            HttpServletRequest httpRequest) {
        orgContext.require(ctx, Permission.AUTOMATION_ACTIVATE);
        return ResponseEntity.ok(automationService.updateStatus(ctx.organizationId(), ctx.userId(), id, request, IpResolverUtil.extractIp(httpRequest)));
    }

    /** Updates the DAG flow (nodes and connections) of an automation. */
    @PutMapping("/{id}/flow")
    public ResponseEntity<AutomationDetailResponse> updateFlow(
            OrgContext ctx,
            @PathVariable UUID id,
            @Valid @RequestBody FlowUpdateRequest request,
            HttpServletRequest httpRequest) {
        orgContext.require(ctx, Permission.AUTOMATION_EDIT);
        return ResponseEntity.ok(automationService.updateFlow(ctx.organizationId(), ctx.userId(), id, request, IpResolverUtil.extractIp(httpRequest)));
    }

    /** Runs the semantic validator ("linter") against the automation's saved flow on demand. */
    @GetMapping("/{id}/validate")
    public ResponseEntity<AutomationValidationResult> validate(
            OrgContext ctx,
            @PathVariable UUID id) {
        orgContext.require(ctx, Permission.AUTOMATION_VIEW);
        return ResponseEntity.ok(automationService.validate(ctx.organizationId(), id));
    }

    /** Replaces the set of user-defined constants for an automation. */
    @PutMapping("/{id}/constants")
    public ResponseEntity<AutomationDetailResponse> updateConstants(
            OrgContext ctx,
            @PathVariable UUID id,
            @Valid @RequestBody ConstantsUpdateRequest request,
            HttpServletRequest httpRequest) {
        orgContext.require(ctx, Permission.AUTOMATION_EDIT);
        return ResponseEntity.ok(automationService.updateConstants(ctx.organizationId(), ctx.userId(), id, request, IpResolverUtil.extractIp(httpRequest)));
    }

    /** Returns paginated execution history for a specific automation. */
    @GetMapping("/{id}/executions")
    public ResponseEntity<Page<AutomationExecutionResponse>> getExecutions(
            OrgContext ctx,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        orgContext.require(ctx, Permission.AUTOMATION_VIEW);
        return ResponseEntity.ok(automationService.getExecutions(ctx.organizationId(), id, PageRequest.of(page, Math.min(size, 100))));
    }

    /**
     * Fires an automation's MANUAL trigger. The optional request body carries user-entered parameter
     * values (keyed by the trigger's parameter-set field names) which are seeded as {@code trigger.*}
     * variables for the run.
     */
    @PostMapping("/{id}/run")
    public ResponseEntity<Void> runManually(
            OrgContext ctx,
            @PathVariable UUID id,
            @RequestBody(required = false) ManualRunRequest request,
            HttpServletRequest httpRequest) {
        orgContext.require(ctx, Permission.AUTOMATION_ACTIVATE);
        Map<String, Object> parameters = (request != null) ? request.parameters() : null;
        automationService.runManually(ctx.organizationId(), ctx.userId(), id, parameters, IpResolverUtil.extractIp(httpRequest));
        return ResponseEntity.accepted().build();
    }

    /** Exports all automations as portable DTOs for backup or migration. */
    @GetMapping("/export")
    public ResponseEntity<List<AutomationExportDto>> exportAll(
            OrgContext ctx) {
        orgContext.require(ctx, Permission.AUTOMATION_VIEW);
        return ResponseEntity.ok(automationService.exportAll(ctx.organizationId()));
    }

    /** Imports automations from a bulk export, creating each as a new entity. */
    @PostMapping("/import")
    public ResponseEntity<ImportResultDto> importAll(
            OrgContext ctx,
            @Valid @RequestBody List<AutomationExportDto> items,
            HttpServletRequest httpRequest) {
        orgContext.require(ctx, Permission.AUTOMATION_EDIT);
        return ResponseEntity.ok(automationService.importAll(ctx.organizationId(), ctx.userId(), items, IpResolverUtil.extractIp(httpRequest)));
    }

    // ─── Test Case Endpoints (org-scoped, AUTOMATION_TEST) ───────────────

    /** Lists all test cases for an automation. */
    @GetMapping("/{id}/tests")
    public ResponseEntity<List<AutomationTestCaseResponse>> getTestCases(
            OrgContext ctx,
            @PathVariable UUID id) {
        orgContext.require(ctx, Permission.AUTOMATION_TEST);
        return ResponseEntity.ok(automationTestService.getTestCases(ctx.organizationId(), id));
    }

    /** Creates a new test case for an automation. */
    @PostMapping("/{id}/tests")
    public ResponseEntity<AutomationTestCaseResponse> createTestCase(
            OrgContext ctx,
            @PathVariable UUID id,
            @Valid @RequestBody AutomationTestCaseRequest request) {
        orgContext.require(ctx, Permission.AUTOMATION_TEST);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(automationTestService.createTestCase(ctx.organizationId(), id, request));
    }

    /** Updates an existing test case. */
    @PutMapping("/{id}/tests/{testId}")
    public ResponseEntity<AutomationTestCaseResponse> updateTestCase(
            OrgContext ctx,
            @PathVariable UUID id,
            @PathVariable UUID testId,
            @Valid @RequestBody AutomationTestCaseRequest request) {
        orgContext.require(ctx, Permission.AUTOMATION_TEST);
        return ResponseEntity.ok(automationTestService.updateTestCase(ctx.organizationId(), id, testId, request));
    }

    /** Deletes a test case. */
    @DeleteMapping("/{id}/tests/{testId}")
    public ResponseEntity<Void> deleteTestCase(
            OrgContext ctx,
            @PathVariable UUID id,
            @PathVariable UUID testId) {
        orgContext.require(ctx, Permission.AUTOMATION_TEST);
        automationTestService.deleteTestCase(ctx.organizationId(), id, testId);
        return ResponseEntity.noContent().build();
    }

    /** Gets the latest result for a test case. */
    @GetMapping("/{id}/tests/{testId}/latest-result")
    public ResponseEntity<AutomationTestResultResponse> getLatestResult(
            OrgContext ctx,
            @PathVariable UUID id,
            @PathVariable UUID testId) {
        orgContext.require(ctx, Permission.AUTOMATION_TEST);
        var result = automationTestService.getLatestResult(ctx.organizationId(), id, testId);
        if (result == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(result);
    }

    /** Runs a single test case in dry-run mode. */
    @PostMapping("/{id}/tests/{testId}/run")
    public ResponseEntity<AutomationTestResultResponse> runTest(
            OrgContext ctx,
            @PathVariable UUID id,
            @PathVariable UUID testId) {
        orgContext.require(ctx, Permission.AUTOMATION_TEST);
        return ResponseEntity.ok(automationTestService.runTest(ctx.organizationId(), id, testId));
    }

    /** Runs all test cases for an automation in dry-run mode. */
    @PostMapping("/{id}/tests/run-all")
    public ResponseEntity<RunAllTestsResponse> runAllTests(
            OrgContext ctx,
            @PathVariable UUID id) {
        orgContext.require(ctx, Permission.AUTOMATION_TEST);
        return ResponseEntity.ok(automationTestService.runAllTests(ctx.organizationId(), id));
    }

    // ─── Test Mode Endpoints (org-scoped, AUTOMATION_TEST) ───────────────

    /** Returns paginated test mode results for an automation. */
    @GetMapping("/{id}/test-mode/results")
    public ResponseEntity<Page<TestModeResultResponse>> getTestModeResults(
            OrgContext ctx,
            @PathVariable UUID id,
            @RequestParam(required = false) String feedback,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        orgContext.require(ctx, Permission.AUTOMATION_TEST);
        return ResponseEntity.ok(testModeService.getResults(ctx.organizationId(), id, feedback, PageRequest.of(page, Math.min(size, 100))));
    }

    /** Submits feedback (CORRECT/INCORRECT) for a test mode result. */
    @PatchMapping("/{id}/test-mode/results/{resultId}/feedback")
    public ResponseEntity<TestModeResultResponse> submitTestModeFeedback(
            OrgContext ctx,
            @PathVariable UUID id,
            @PathVariable UUID resultId,
            @Valid @RequestBody TestModeFeedbackRequest request) {
        orgContext.require(ctx, Permission.AUTOMATION_TEST);
        return ResponseEntity.ok(testModeService.submitFeedback(ctx.organizationId(), id, resultId, request));
    }

    /** Returns aggregated test mode statistics for an automation. */
    @GetMapping("/{id}/test-mode/stats")
    public ResponseEntity<TestModeStatsResponse> getTestModeStats(
            OrgContext ctx,
            @PathVariable UUID id) {
        orgContext.require(ctx, Permission.AUTOMATION_TEST);
        return ResponseEntity.ok(testModeService.getStats(ctx.organizationId(), id));
    }

    /** Clears all test mode results for an automation. */
    @DeleteMapping("/{id}/test-mode/results")
    public ResponseEntity<Void> clearTestModeResults(
            OrgContext ctx,
            @PathVariable UUID id) {
        orgContext.require(ctx, Permission.AUTOMATION_TEST);
        testModeService.clearResults(ctx.organizationId(), id);
        return ResponseEntity.noContent().build();
    }
}
