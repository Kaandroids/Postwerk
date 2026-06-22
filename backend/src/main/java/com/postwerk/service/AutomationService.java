package com.postwerk.service;

import com.postwerk.dto.*;
import com.postwerk.dto.automation.AutomationValidationResult;
import com.postwerk.model.Automation;
import com.postwerk.model.enums.AutomationKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Service interface for managing email automation workflows, including CRUD operations,
 * flow graph updates (nodes and edges), execution history, manual triggering, and bulk import/export.
 *
 * @since 1.0
 */
public interface AutomationService {

    AutomationResponse create(UUID organizationId, UUID actingUserId, AutomationRequest request, String ipAddress);

    List<AutomationResponse> listByOrg(UUID organizationId);

    /** Lists the organization's reusable, trigger-less integrations ({@code kind == INTEGRATION}). */
    List<AutomationResponse> listIntegrations(UUID organizationId);

    AutomationDetailResponse getById(UUID organizationId, UUID automationId);

    AutomationResponse update(UUID organizationId, UUID actingUserId, UUID automationId, AutomationRequest request, String ipAddress);

    void delete(UUID organizationId, UUID actingUserId, UUID automationId, String ipAddress);

    AutomationResponse updateStatus(UUID organizationId, UUID actingUserId, UUID automationId, AutomationStatusRequest request, String ipAddress);

    AutomationDetailResponse updateFlow(UUID organizationId, UUID actingUserId, UUID automationId, FlowUpdateRequest request, String ipAddress);

    AutomationDetailResponse updateConstants(UUID organizationId, UUID actingUserId, UUID automationId, ConstantsUpdateRequest request, String ipAddress);

    Page<AutomationExecutionResponse> getExecutions(UUID organizationId, UUID automationId, Pageable pageable);

    /**
     * Fires an automation's MANUAL trigger on demand. The user-entered {@code parameters} (keyed by the
     * trigger's parameter-set field names) are seeded as {@code trigger.*} execution variables, then the
     * flow runs live via the same off-request path as inbound webhooks. Requires the automation to be
     * ACTIVE and to contain a TRIGGER node in {@code MANUAL} mode.
     */
    void runManually(UUID organizationId, UUID actingUserId, UUID automationId,
                     java.util.Map<String, Object> parameters, String ipAddress);

    List<AutomationExportDto> exportAll(UUID organizationId);

    ImportResultDto importAll(UUID organizationId, UUID actingUserId, List<AutomationExportDto> items, String ipAddress);

    AutomationResponse toggleLock(UUID organizationId, UUID automationId);

    boolean isLocked(UUID organizationId, UUID automationId);

    /**
     * Runs the semantic {@link AutomationValidator} against the automation's saved flow.
     * Used by the on-demand validate endpoint, the AI self-correction loop, and the activate gate.
     */
    AutomationValidationResult validate(UUID organizationId, UUID automationId);

    /**
     * Deep-copies a source automation's flow into a new buyer-owned automation for the marketplace
     * install path. Nodes/edges are recreated; each node's config JSON is transformed by
     * {@code configRewriter} (resource id remapping + account clearing); the raw constants JSONB is
     * copied verbatim so encrypted secrets are preserved. The copy is created PAUSED with no bound
     * accounts; buyer-owned inbound webhook endpoints are reconciled for any WEBHOOK trigger.
     *
     * @param buyerOrgId     the buyer's active organization that will own the copy (#4) — the org the
     *                       install was made from, so its plan/quota governs the copy when it runs
     * @param buyerId        the user that will own the copy (audit/attribution)
     * @param source         the author's source automation entity
     * @param configRewriter transform applied to each node's config JSON
     * @param hidden         whether the copy is content-hidden (PRIVATE listing)
     * @param locked         whether the copy is locked from editing
     * @return the new automation id
     */
    UUID installCopy(UUID buyerOrgId, UUID buyerId, Automation source, UnaryOperator<String> configRewriter,
                     boolean hidden, boolean locked);

    /**
     * Creates a new automation + flow from explicit specs (not from a live source entity). Used to
     * materialize a buyer-owned copy from a marketplace snapshot manifest. Mirrors {@link #installCopy}
     * (PAUSED, no bound accounts, webhook endpoints reconciled) but reads nodes/edges from
     * {@link NodeSpec}/{@link EdgeSpec} lists, so it does not depend on the author's live automation.
     *
     * @return the new automation id
     */
    UUID createSnapshotCopy(UUID organizationId, UUID userId, String name, String description,
                            AutomationKind kind, String color, String constants, String flowData,
                            java.util.List<NodeSpec> nodes, java.util.List<EdgeSpec> edges,
                            boolean hidden, boolean locked);
}
