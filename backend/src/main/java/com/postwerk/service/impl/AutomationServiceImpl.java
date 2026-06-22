package com.postwerk.service.impl;

import com.postwerk.service.AutomationConstantsCodec;
import com.postwerk.service.AutomationValidator;
import com.postwerk.dto.*;
import com.postwerk.dto.automation.AutomationValidationResult;
import com.postwerk.model.*;
import com.postwerk.model.enums.AutomationKind;
import com.postwerk.model.enums.AutomationStatus;
import com.postwerk.model.enums.AutomationType;
import com.postwerk.model.enums.NodeType;
import com.postwerk.model.enums.Permission;
import com.postwerk.repository.*;
import com.postwerk.service.AuditService;
import com.postwerk.service.AutomationExecutorService;
import com.postwerk.service.AutomationService;
import com.postwerk.service.OrgContext;
import com.postwerk.service.OrgContextService;
import com.postwerk.service.QuotaService;
import com.postwerk.service.WebhookEndpointReconciler;
import com.postwerk.util.EnumUtil;
import com.postwerk.util.ImportHelper;
import com.postwerk.util.RepositoryHelper;
import com.postwerk.util.UuidUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link AutomationService}.
 *
 * <p>Manages the full lifecycle of graph-based email automations, including CRUD for
 * automation definitions, node/edge flow updates with ID remapping, status toggling,
 * execution history retrieval, and bulk import/export. Synchronizes trigger account IDs
 * from node configuration back to the automation entity.</p>
 *
 * @since 1.0
 */
@Service
public class AutomationServiceImpl implements AutomationService {

    private static final Logger log = LoggerFactory.getLogger(AutomationServiceImpl.class);

    private final AutomationRepository automationRepository;
    private final AutomationNodeRepository nodeRepository;
    private final AutomationEdgeRepository edgeRepository;
    private final AutomationExecutionRepository executionRepository;
    private final AutomationTestModeResultRepository testModeResultRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final QuotaService quotaService;
    private final WebhookEndpointReconciler webhookEndpointReconciler;
    private final MarketplaceListingRepository marketplaceListingRepository;
    private final AutomationConstantsCodec constantsCodec;
    private final AutomationValidator automationValidator;
    private final OrgContextService orgContextService;
    private final AutomationExecutorService executorService;
    private final EmailAccountRepository emailAccountRepository;

    public AutomationServiceImpl(AutomationRepository automationRepository,
                                  AutomationNodeRepository nodeRepository,
                                  AutomationEdgeRepository edgeRepository,
                                  AutomationExecutionRepository executionRepository,
                                  AutomationTestModeResultRepository testModeResultRepository,
                                  AuditService auditService,
                                  ObjectMapper objectMapper,
                                  QuotaService quotaService,
                                  WebhookEndpointReconciler webhookEndpointReconciler,
                                  MarketplaceListingRepository marketplaceListingRepository,
                                  AutomationConstantsCodec constantsCodec,
                                  AutomationValidator automationValidator,
                                  OrgContextService orgContextService,
                                  AutomationExecutorService executorService,
                                  EmailAccountRepository emailAccountRepository) {
        this.automationRepository = automationRepository;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.executionRepository = executionRepository;
        this.testModeResultRepository = testModeResultRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.quotaService = quotaService;
        this.webhookEndpointReconciler = webhookEndpointReconciler;
        this.marketplaceListingRepository = marketplaceListingRepository;
        this.constantsCodec = constantsCodec;
        this.automationValidator = automationValidator;
        this.orgContextService = orgContextService;
        this.executorService = executorService;
        this.emailAccountRepository = emailAccountRepository;
    }

    @Override
    @Transactional
    public AutomationResponse create(UUID organizationId, UUID actingUserId, AutomationRequest request, String ipAddress) {
        quotaService.checkAutomationQuota(organizationId);
        AutomationKind kind = parseKind(request.kind());
        var automation = Automation.builder()
                .userId(actingUserId)
                .organizationId(organizationId)
                .name(request.name())
                .description(request.description())
                .type(AutomationType.EMAIL)
                .kind(kind)
                .status(AutomationStatus.PAUSED)
                .accountIds(new UUID[0])
                .color(request.color() != null ? request.color() : "#3b82f6")
                .build();

        var saved = automationRepository.save(automation);
        auditService.log(actingUserId, AuditAction.AUTOMATION_CREATED, "Automation: " + saved.getName(), ipAddress);
        return toResponse(saved, 0, 0);
    }

    @Override
    public List<AutomationResponse> listByOrg(UUID organizationId) {
        var automations = automationRepository.findTop200ByOrganizationIdOrderByCreatedAtDesc(organizationId);
        List<UUID> ids = automations.stream().map(Automation::getId).toList();

        Map<UUID, long[]> statsMap = new HashMap<>();
        Map<UUID, Integer> nodeCountMap = new HashMap<>();
        Map<UUID, Integer> edgeCountMap = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : executionRepository.statsForAutomations(ids)) {
                UUID aId = (UUID) row[0];
                long total = ((Number) row[1]).longValue();
                long success = ((Number) row[2]).longValue();
                long failed = ((Number) row[3]).longValue();
                statsMap.put(aId, new long[]{total, success, failed});
            }
            for (Object[] row : nodeRepository.countByAutomationIds(ids)) {
                nodeCountMap.put((UUID) row[0], ((Number) row[1]).intValue());
            }
            for (Object[] row : edgeRepository.countByAutomationIds(ids)) {
                edgeCountMap.put((UUID) row[0], ((Number) row[1]).intValue());
            }
        }

        Map<UUID, String> triggerModeMap = triggerModesFor(ids);

        return automations.stream()
                .filter(a -> !a.isHidden())
                .filter(a -> a.getKind() == AutomationKind.AUTOMATION)
                .map(a -> {
                    int nodeCount = nodeCountMap.getOrDefault(a.getId(), 0);
                    int edgeCount = edgeCountMap.getOrDefault(a.getId(), 0);
                    long[] stats = statsMap.getOrDefault(a.getId(), new long[]{0, 0, 0});
                    return toResponse(a, nodeCount, edgeCount, stats[0], stats[1], stats[2],
                            triggerModeMap.get(a.getId()));
                })
                .toList();
    }

    /** Batch-resolves each automation's TRIGGER-node mode (EMAIL/WEBHOOK/CRON/MANUAL) for the list view. */
    private Map<UUID, String> triggerModesFor(List<UUID> automationIds) {
        if (automationIds.isEmpty()) return Map.of();
        Map<UUID, String> modes = new HashMap<>();
        for (Object[] row : nodeRepository.findTriggerConfigsByAutomationIds(automationIds)) {
            UUID automationId = (UUID) row[0];
            String config = (String) row[1];
            modes.putIfAbsent(automationId, readTriggerModeFromConfig(config));
        }
        return modes;
    }

    private String readTriggerModeFromConfig(String config) {
        try {
            JsonNode c = objectMapper.readTree(config == null || config.isBlank() ? "{}" : config);
            return c.path("triggerMode").asText("EMAIL");
        } catch (Exception e) {
            return "EMAIL";
        }
    }

    @Override
    public List<AutomationResponse> listIntegrations(UUID organizationId) {
        var automations = automationRepository.findTop200ByOrganizationIdOrderByCreatedAtDesc(organizationId);
        List<UUID> ids = automations.stream()
                .filter(a -> a.getKind() == AutomationKind.INTEGRATION && !a.isHidden())
                .map(Automation::getId).toList();

        Map<UUID, Integer> nodeCountMap = new HashMap<>();
        Map<UUID, Integer> edgeCountMap = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : nodeRepository.countByAutomationIds(ids)) {
                nodeCountMap.put((UUID) row[0], ((Number) row[1]).intValue());
            }
            for (Object[] row : edgeRepository.countByAutomationIds(ids)) {
                edgeCountMap.put((UUID) row[0], ((Number) row[1]).intValue());
            }
        }

        return automations.stream()
                .filter(a -> !a.isHidden())
                .filter(a -> a.getKind() == AutomationKind.INTEGRATION)
                .map(a -> toResponse(a, nodeCountMap.getOrDefault(a.getId(), 0),
                        edgeCountMap.getOrDefault(a.getId(), 0)))
                .toList();
    }

    private AutomationKind parseKind(String kind) {
        return EnumUtil.parseOrDefault(AutomationKind.class, kind, AutomationKind.AUTOMATION);
    }

    @Override
    @Transactional(readOnly = true)
    public AutomationDetailResponse getById(UUID organizationId, UUID automationId) {
        var automation = findByOrgAndId(organizationId, automationId);
        // Hidden copies (PRIVATE marketplace installs) never expose nodes/edges/constant values
        // via the normal automation API — only metadata + masked constants. Configure them through
        // the marketplace endpoints instead.
        if (automation.isHidden()) {
            return new AutomationDetailResponse(
                    automation.getId(), automation.getName(), automation.getDescription(),
                    automation.getType().name(), automation.getKind().name(), automation.getStatus().name(),
                    automation.getColor(), "{}",
                    List.of(), List.of(), List.of(),
                    automation.getLastRunAt(), automation.isLocked(),
                    automation.getCreatedAt(), automation.getUpdatedAt());
        }
        var nodes = nodeRepository.findByAutomationId(automationId);
        var edges = edgeRepository.findByAutomationId(automationId);
        return toDetailResponse(automation, nodes, edges);
    }

    @Override
    @Transactional
    public AutomationResponse update(UUID organizationId, UUID actingUserId, UUID automationId, AutomationRequest request, String ipAddress) {
        var automation = findByOrgAndId(organizationId, automationId);

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("name", automation.getName());
        before.put("description", automation.getDescription() != null ? automation.getDescription() : "");
        before.put("active", automation.getStatus().name());

        automation.setName(request.name());
        automation.setDescription(request.description());
        if (request.color() != null) {
            automation.setColor(request.color());
        }

        var saved = automationRepository.save(automation);
        int nodeCount = nodeRepository.countByAutomationId(automationId);
        int edgeCount = edgeRepository.countByAutomationId(automationId);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("name", saved.getName());
        after.put("description", saved.getDescription() != null ? saved.getDescription() : "");
        after.put("active", saved.getStatus().name());

        auditService.logDiff(actingUserId, AuditAction.AUTOMATION_UPDATED, before, after, "Automation: " + saved.getName(), ipAddress);
        return toResponse(saved, nodeCount, edgeCount);
    }

    @Override
    @Transactional
    public void delete(UUID organizationId, UUID actingUserId, UUID automationId, String ipAddress) {
        var automation = findByOrgAndId(organizationId, automationId);
        marketplaceListingRepository.findByAutomationIdAndDeletedAtIsNull(automationId).ifPresent(listing -> {
            throw new IllegalArgumentException(
                    "This automation is published on the marketplace. Unpublish the listing before deleting it.");
        });
        automationRepository.delete(automation);
        auditService.log(actingUserId, AuditAction.AUTOMATION_DELETED, "Automation: " + automation.getName(), ipAddress);
    }

    @Override
    @Transactional
    public AutomationResponse updateStatus(UUID organizationId, UUID actingUserId, UUID automationId, AutomationStatusRequest request, String ipAddress) {
        var automation = findByOrgAndId(organizationId, automationId);
        AutomationStatus newStatus = AutomationStatus.valueOf(request.status());

        // Role gate (#4 ladder): taking a flow live (ACTIVE/TESTING) requires AUTOMATION_ACTIVATE
        // (Admin/Owner only) — the Editor builds but never starts. Pausing requires AUTOMATION_EDIT
        // (Editor+), so Members/Viewers cannot change status at all.
        OrgContext ctx = orgContextService.resolve(actingUserId, organizationId.toString());
        orgContextService.require(ctx, newStatus == AutomationStatus.PAUSED
                ? Permission.AUTOMATION_EDIT : Permission.AUTOMATION_ACTIVATE);

        // Activating enforces the semantic rule catalog: a flow with error-severity issues cannot
        // go live (PAUSED/TESTING are never blocked so work-in-progress can still be saved/tested).
        if (newStatus == AutomationStatus.ACTIVE) {
            var result = runValidator(automation);
            if (!result.valid()) {
                throw new IllegalArgumentException(
                        "Cannot activate: please fix these problems first — " + result.errorSummary());
            }
            // Going live can send real mail (#4): the activator must hold a SEND grant on every
            // mailbox the flow would send from. Owner/Admin bypass via allMailboxAccess.
            requireSendGrantsForActivation(organizationId, actingUserId, automation);
        }

        automation.setStatus(newStatus);

        var saved = automationRepository.save(automation);
        AuditAction action = newStatus == AutomationStatus.ACTIVE
                ? AuditAction.AUTOMATION_ACTIVATED
                : AuditAction.AUTOMATION_PAUSED;
        auditService.log(actingUserId, action, "Automation: " + saved.getName(), ipAddress);

        int nodeCount = nodeRepository.countByAutomationId(automationId);
        int edgeCount = edgeRepository.countByAutomationId(automationId);
        return toResponse(saved, nodeCount, edgeCount);
    }

    @Override
    @Transactional
    public AutomationDetailResponse updateFlow(UUID organizationId, UUID actingUserId, UUID automationId, FlowUpdateRequest request, String ipAddress) {
        var automation = findByOrgAndId(organizationId, automationId);
        // Role gate (#4 ladder): editing the flow requires AUTOMATION_EDIT (Editor+). Members/Viewers
        // get a read-only editor on the frontend; this is the matching server-side enforcement.
        orgContextService.require(
                orgContextService.resolve(actingUserId, organizationId.toString()), Permission.AUTOMATION_EDIT);
        if (automation.isHidden()) {
            throw new IllegalStateException("This automation's content is protected and cannot be edited");
        }

        if (request.viewport() != null) {
            automation.setFlowData(request.viewport());
        }

        edgeRepository.deleteByAutomationId(automationId);
        nodeRepository.deleteByAutomationId(automationId);
        edgeRepository.flush();
        nodeRepository.flush();

        var nodeInputs = request.nodes().stream()
                .map(n -> new NodeInput(n.id(), n.nodeType(), n.label(), n.positionX(), n.positionY(), n.config(), n.nodeKey()))
                .toList();
        var edgeInputs = request.edges().stream()
                .map(e -> new EdgeInput(e.sourceNodeId(), e.sourceHandle(), e.targetNodeId(), e.targetHandle()))
                .toList();

        validateFlowNodes(automation.getKind(), nodeInputs);

        var result = buildFlow(automation, nodeInputs, edgeInputs);

        syncTriggerAccountIds(automation, request);
        webhookEndpointReconciler.reconcile(automation, result.nodes());
        automationRepository.save(automation);
        auditService.log(actingUserId, AuditAction.AUTOMATION_UPDATED, "Flow updated: " + automation.getName(), ipAddress);
        return toDetailResponse(automation, result.nodes(), result.edges());
    }

    @Override
    @Transactional
    public AutomationDetailResponse updateConstants(UUID organizationId, UUID actingUserId, UUID automationId, ConstantsUpdateRequest request, String ipAddress) {
        var automation = findByOrgAndId(organizationId, automationId);
        if (automation.isHidden()) {
            throw new IllegalStateException("This automation's content is protected; configure it via the marketplace");
        }

        automation.setConstants(buildConstantsJson(request.constants(), automation.getConstants(), true));

        var saved = automationRepository.save(automation);
        auditService.log(actingUserId, AuditAction.AUTOMATION_UPDATED, "Constants updated: " + saved.getName(), ipAddress);

        var nodes = nodeRepository.findByAutomationId(automationId);
        var edges = edgeRepository.findByAutomationId(automationId);
        return toDetailResponse(saved, nodes, edges);
    }

    @Override
    public Page<AutomationExecutionResponse> getExecutions(UUID organizationId, UUID automationId, Pageable pageable) {
        findByOrgAndId(organizationId, automationId);
        return executionRepository.findByAutomationIdOrderByTriggeredAtDesc(automationId, pageable)
                .map(this::toExecutionResponse);
    }

    @Override
    @Transactional
    public void runManually(UUID organizationId, UUID actingUserId, UUID automationId,
                            Map<String, Object> parameters, String ipAddress) {
        var automation = findByOrgAndId(organizationId, automationId);

        if (automation.getStatus() != AutomationStatus.ACTIVE) {
            throw new IllegalArgumentException("Only active automations can be run manually. Activate it first.");
        }

        AutomationNode triggerNode = nodeRepository.findByAutomationId(automationId).stream()
                .filter(n -> n.getNodeType() == NodeType.TRIGGER && isManualTrigger(n))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "This automation has no manual trigger and cannot be run manually."));

        // Seed the user-entered values as trigger.* variables (mirrors the inbound-webhook payload mapping),
        // then run live through the same off-request executor path.
        Map<String, Object> triggerVars = new LinkedHashMap<>();
        if (parameters != null) {
            parameters.forEach((k, v) -> triggerVars.put("trigger." + k, v));
        }
        triggerVars.put("trigger.receivedAt", Instant.now().toString());

        EmailAccount account = resolveManualRunAccount(automation);
        Email syntheticEmail = (account != null) ? buildManualSyntheticEmail(account, automation) : null;

        auditService.log(actingUserId, AuditAction.AUTOMATION_EXECUTED, "Manual run: " + automation.getName(), ipAddress);

        executorService.runInboundWebhook(automation, triggerNode.getId(), account, syntheticEmail, triggerVars);
    }

    /** True if the TRIGGER node is configured in MANUAL mode. */
    private boolean isManualTrigger(AutomationNode node) {
        try {
            JsonNode config = objectMapper.readTree(node.getConfig() == null ? "{}" : node.getConfig());
            return "MANUAL".equals(config.path("triggerMode").asText("EMAIL"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Resolves the bound email account for a manual run so downstream email actions have a mailbox.
     * No bound account → {@code null} (account-less run, only non-email actions work); a configured but
     * missing account is a genuine misconfiguration → 400.
     */
    private EmailAccount resolveManualRunAccount(Automation automation) {
        UUID[] accountIds = automation.getAccountIds();
        if (accountIds == null || accountIds.length == 0) {
            return null;
        }
        return emailAccountRepository.findById(accountIds[0])
                .orElseThrow(() -> new IllegalArgumentException(
                        "This automation references an email account that no longer exists."));
    }

    /** Builds a non-persisted synthetic email to carry the manual run's account context + trace FK. */
    private Email buildManualSyntheticEmail(EmailAccount account, Automation automation) {
        Instant now = Instant.now();
        return Email.builder()
                .id(UUID.randomUUID())
                .emailAccountId(account.getId())
                .messageId("manual-" + UUID.randomUUID())
                .folder("INBOX")
                .subject("Manual run: " + automation.getName())
                .bodyText("")
                .snippet("")
                .receivedAt(now)
                .isRead(true)
                .isStarred(false)
                .hasAttachments(false)
                .processed(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AutomationExportDto> exportAll(UUID organizationId) {
        var automations = automationRepository.findByOrganizationId(organizationId);
        List<UUID> automationIds = automations.stream().map(Automation::getId).toList();

        // Batch-load every node and edge for the whole page up front, then group by automation id in
        // memory — avoids the 1+2A per-automation queries the per-item lambda used to fire (N+1 fix).
        Map<UUID, List<AutomationNode>> nodesByAutomation = automationIds.isEmpty()
                ? Map.of()
                : nodeRepository.findByAutomationIdIn(automationIds).stream()
                        .collect(Collectors.groupingBy(n -> n.getAutomation().getId()));
        Map<UUID, List<AutomationEdge>> edgesByAutomation = automationIds.isEmpty()
                ? Map.of()
                : edgeRepository.findByAutomationIdIn(automationIds).stream()
                        .collect(Collectors.groupingBy(e -> e.getAutomation().getId()));

        return automations.stream()
                .map(a -> {
                    var nodes = nodesByAutomation.getOrDefault(a.getId(), List.of());
                    var edges = edgesByAutomation.getOrDefault(a.getId(), List.of());

                    // Build node ID -> tempId mapping
                    Map<UUID, String> nodeIdMap = new HashMap<>();
                    List<AutomationExportDto.AutomationNodeExportDto> nodeDtos = new ArrayList<>();
                    for (int i = 0; i < nodes.size(); i++) {
                        var n = nodes.get(i);
                        String tempId = "node_" + i;
                        nodeIdMap.put(n.getId(), tempId);
                        nodeDtos.add(new AutomationExportDto.AutomationNodeExportDto(
                                tempId, n.getNodeType().name(), n.getLabel(),
                                n.getPositionX(), n.getPositionY(), n.getConfig()));
                    }

                    List<AutomationExportDto.AutomationEdgeExportDto> edgeDtos = edges.stream()
                            .map(e -> new AutomationExportDto.AutomationEdgeExportDto(
                                    nodeIdMap.getOrDefault(e.getSourceNode().getId(), ""),
                                    e.getSourceHandle(),
                                    nodeIdMap.getOrDefault(e.getTargetNode().getId(), ""),
                                    e.getTargetHandle()))
                            .toList();

                    return new AutomationExportDto(
                            a.getName(), a.getDescription(), a.getColor(),
                            a.getStatus().name(), nodeDtos, edgeDtos, a.getFlowData(),
                            parseConstants(a.getConstants()));
                })
                .toList();
    }

    @Override
    @Transactional
    public ImportResultDto importAll(UUID organizationId, UUID actingUserId, List<AutomationExportDto> items, String ipAddress) {
        return ImportHelper.runImport(items, AutomationExportDto::name, item -> {
            quotaService.checkAutomationQuota(organizationId);
            var automation = Automation.builder()
                    .userId(actingUserId)
                    .organizationId(organizationId)
                    .name(item.name())
                    .description(item.description())
                    .type(AutomationType.EMAIL)
                    .status(AutomationStatus.PAUSED)
                    .accountIds(new UUID[0])
                    .color(item.color() != null ? item.color() : "#3b82f6")
                    .flowData(item.flowData())
                    .constants(buildConstantsJson(item.constants(), null, false))
                    .build();
            var saved = automationRepository.save(automation);

            var nodeInputs = item.nodes() != null ? item.nodes().stream()
                    .map(n -> new NodeInput(n.tempId(), n.nodeType(), n.label(), n.positionX(), n.positionY(), n.config(), null))
                    .toList() : List.<NodeInput>of();
            var edgeInputs = item.edges() != null ? item.edges().stream()
                    .map(e -> new EdgeInput(e.sourceTempId(), e.sourceHandle(), e.targetTempId(), e.targetHandle()))
                    .toList() : List.<EdgeInput>of();

            buildFlow(saved, nodeInputs, edgeInputs);
            auditService.log(actingUserId, AuditAction.AUTOMATION_CREATED, "Automation imported: " + saved.getName(), ipAddress);
        });
    }

    // ─── Flow builder helpers (shared between updateFlow and importAll) ────

    private record NodeInput(String tempId, String nodeType, String label, double positionX, double positionY, String config, String nodeKey) {}
    private record EdgeInput(String sourceId, String sourceHandle, String targetId, String targetHandle) {}
    private record FlowBuildResult(List<AutomationNode> nodes, List<AutomationEdge> edges) {}

    /**
     * Enforces the structural contract for each automation kind on flow save:
     * <ul>
     *   <li>{@code INTEGRATION}: exactly one INPUT node, at most one OUTPUT node, and no TRIGGER node.</li>
     *   <li>{@code AUTOMATION}: must not contain INPUT or OUTPUT nodes (those belong to integrations).</li>
     * </ul>
     */
    private void validateFlowNodes(AutomationKind kind, List<NodeInput> nodeInputs) {
        long inputs = nodeInputs.stream().filter(n -> "INPUT".equals(n.nodeType())).count();
        long outputs = nodeInputs.stream().filter(n -> "OUTPUT".equals(n.nodeType())).count();
        long triggers = nodeInputs.stream().filter(n -> "TRIGGER".equals(n.nodeType())).count();

        if (kind == AutomationKind.INTEGRATION) {
            if (inputs != 1) {
                throw new IllegalArgumentException("An integration must have exactly one INPUT node");
            }
            if (outputs > 1) {
                throw new IllegalArgumentException("An integration may have at most one OUTPUT node");
            }
            if (triggers > 0) {
                throw new IllegalArgumentException("An integration cannot contain a TRIGGER node");
            }
        } else {
            if (inputs > 0 || outputs > 0) {
                throw new IllegalArgumentException("INPUT/OUTPUT nodes are only allowed in integrations");
            }
        }
    }

    private FlowBuildResult buildFlow(Automation automation, List<NodeInput> nodeInputs, List<EdgeInput> edgeInputs) {
        Map<String, UUID> idMapping = new HashMap<>();
        Map<String, String> keyMapping = new HashMap<>();
        List<AutomationNode> newNodes = new ArrayList<>();

        // Friendly, stable node-scoped variable key: preserve a provided one, else generate the next free number.
        java.util.Set<String> usedKeys = new java.util.HashSet<>();
        for (var ni : nodeInputs) {
            if (ni.nodeKey() != null && !ni.nodeKey().isBlank()) usedKeys.add(ni.nodeKey());
        }
        int keyCounter = 0;

        for (var ni : nodeInputs) {
            String nodeKey = (ni.nodeKey() != null && !ni.nodeKey().isBlank()) ? ni.nodeKey() : null;
            if (nodeKey == null) {
                do { nodeKey = String.valueOf(++keyCounter); } while (usedKeys.contains(nodeKey));
                usedKeys.add(nodeKey);
            }
            var node = AutomationNode.builder()
                    .automation(automation)
                    .nodeType(NodeType.valueOf(ni.nodeType()))
                    .label(ni.label())
                    .nodeKey(nodeKey)
                    .positionX(ni.positionX())
                    .positionY(ni.positionY())
                    .config(ni.config() != null ? ni.config() : "{}")
                    .build();
            var saved = nodeRepository.save(node);
            newNodes.add(saved);
            if (ni.tempId() != null) {
                idMapping.put(ni.tempId(), saved.getId());
                keyMapping.put(ni.tempId(), nodeKey);
            }
        }

        // Rewrite node-scoped variable tokens that still reference a temp id (new nodes) onto the nodeKey,
        // so downstream {{http_<key>.x}} / {{vectorsearch_<key>.x}} / {{integration_<key>.x}} resolve at runtime.
        for (var node : newNodes) {
            String cfg = node.getConfig();
            if (cfg == null || cfg.isBlank()) continue;
            String rewritten = cfg;
            for (var entry : keyMapping.entrySet()) {
                String tempId = entry.getKey();
                String key = entry.getValue();
                if (tempId.equals(key)) continue;
                rewritten = rewritten
                        .replace("http_" + tempId + ".", "http_" + key + ".")
                        .replace("vectorsearch_" + tempId + ".", "vectorsearch_" + key + ".")
                        .replace("integration_" + tempId + ".", "integration_" + key + ".");
            }
            if (!rewritten.equals(cfg)) {
                node.setConfig(rewritten);
                nodeRepository.save(node);
            }
        }

        List<AutomationEdge> newEdges = new ArrayList<>();
        for (var ei : edgeInputs) {
            UUID sourceId = idMapping.getOrDefault(ei.sourceId(), UuidUtil.parseOrNull(ei.sourceId()));
            UUID targetId = idMapping.getOrDefault(ei.targetId(), UuidUtil.parseOrNull(ei.targetId()));
            if (sourceId == null || targetId == null) continue;

            AutomationNode sourceNode = newNodes.stream()
                    .filter(n -> n.getId().equals(sourceId)).findFirst().orElse(null);
            AutomationNode targetNode = newNodes.stream()
                    .filter(n -> n.getId().equals(targetId)).findFirst().orElse(null);
            if (sourceNode == null || targetNode == null) continue;

            var edge = AutomationEdge.builder()
                    .automation(automation)
                    .sourceNode(sourceNode)
                    .sourceHandle(ei.sourceHandle() != null ? ei.sourceHandle() : "output")
                    .targetNode(targetNode)
                    .targetHandle(ei.targetHandle() != null ? ei.targetHandle() : "input")
                    .build();
            newEdges.add(edgeRepository.save(edge));
        }

        return new FlowBuildResult(newNodes, newEdges);
    }

    @Override
    @Transactional
    public AutomationResponse toggleLock(UUID organizationId, UUID automationId) {
        var automation = findByOrgAndId(organizationId, automationId);
        automation.setLocked(!automation.isLocked());
        return toResponse(automationRepository.save(automation), 0, 0);
    }

    @Override
    public boolean isLocked(UUID organizationId, UUID automationId) {
        return findByOrgAndId(organizationId, automationId).isLocked();
    }

    @Override
    @Transactional
    public UUID installCopy(UUID buyerOrgId, UUID buyerId, Automation source,
                            java.util.function.UnaryOperator<String> configRewriter,
                            boolean hidden, boolean locked) {
        // The buyer-owned copy lands in the org the install was made from (#4), so its plan/quota
        // governs the copy when it runs — not the buyer's personal org, not the author's org.
        quotaService.checkAutomationQuota(buyerOrgId);
        var sourceNodes = nodeRepository.findByAutomationId(source.getId());
        var sourceEdges = edgeRepository.findByAutomationId(source.getId());

        var copy = Automation.builder()
                .userId(buyerId)
                .organizationId(buyerOrgId)
                .name(source.getName())
                .description(source.getDescription())
                .type(AutomationType.EMAIL)
                .kind(source.getKind())
                .status(AutomationStatus.PAUSED)
                .accountIds(new UUID[0])
                .color(source.getColor() != null ? source.getColor() : "#3b82f6")
                .flowData(source.getFlowData())
                .constants(source.getConstants() != null ? source.getConstants() : "{}")
                .hidden(hidden)
                .locked(locked)
                .build();
        var saved = automationRepository.save(copy);

        Map<UUID, String> tempIds = new HashMap<>();
        List<NodeInput> nodeInputs = new ArrayList<>();
        for (int i = 0; i < sourceNodes.size(); i++) {
            var n = sourceNodes.get(i);
            String tempId = "node_" + i;
            tempIds.put(n.getId(), tempId);
            String cfg = configRewriter.apply(n.getConfig() != null ? n.getConfig() : "{}");
            nodeInputs.add(new NodeInput(tempId, n.getNodeType().name(), n.getLabel(),
                    n.getPositionX(), n.getPositionY(), cfg, n.getNodeKey()));
        }
        List<EdgeInput> edgeInputs = new ArrayList<>();
        for (var e : sourceEdges) {
            edgeInputs.add(new EdgeInput(
                    tempIds.get(e.getSourceNode().getId()), e.getSourceHandle(),
                    tempIds.get(e.getTargetNode().getId()), e.getTargetHandle()));
        }

        var result = buildFlow(saved, nodeInputs, edgeInputs);
        webhookEndpointReconciler.reconcile(saved, result.nodes());
        automationRepository.save(saved);
        return saved.getId();
    }

    @Override
    @Transactional
    public UUID createSnapshotCopy(UUID organizationId, UUID userId, String name, String description,
                                   AutomationKind kind, String color, String constants, String flowData,
                                   List<NodeSpec> nodes, List<EdgeSpec> edges, boolean hidden, boolean locked) {
        quotaService.checkAutomationQuota(organizationId);

        var copy = Automation.builder()
                .userId(userId)
                .organizationId(organizationId)
                .name(name)
                .description(description)
                .type(AutomationType.EMAIL)
                .kind(kind != null ? kind : AutomationKind.AUTOMATION)
                .status(AutomationStatus.PAUSED)
                .accountIds(new UUID[0])
                .color(color != null ? color : "#3b82f6")
                .flowData(flowData)
                .constants(constants != null ? constants : "{}")
                .hidden(hidden)
                .locked(locked)
                .build();
        var saved = automationRepository.save(copy);

        List<NodeInput> nodeInputs = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            var n = nodes.get(i);
            nodeInputs.add(new NodeInput("node_" + i, n.nodeType(), n.label(),
                    n.positionX(), n.positionY(), n.config() != null ? n.config() : "{}", null));
        }
        List<EdgeInput> edgeInputs = new ArrayList<>();
        for (var e : edges) {
            edgeInputs.add(new EdgeInput("node_" + e.sourceIndex(), e.sourceHandle(),
                    "node_" + e.targetIndex(), e.targetHandle()));
        }

        var result = buildFlow(saved, nodeInputs, edgeInputs);
        webhookEndpointReconciler.reconcile(saved, result.nodes());
        automationRepository.save(saved);
        return saved.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public AutomationValidationResult validate(UUID organizationId, UUID automationId) {
        return runValidator(findByOrgAndId(organizationId, automationId));
    }

    /** Loads the automation's saved flow and runs the semantic {@link AutomationValidator} on it. */
    private AutomationValidationResult runValidator(Automation automation) {
        var nodes = nodeRepository.findByAutomationId(automation.getId());
        var edges = edgeRepository.findByAutomationId(automation.getId());

        var nodeViews = nodes.stream()
                .map(n -> new AutomationValidator.NodeView(
                        n.getId().toString(), n.getNodeType().name(), n.getLabel(), n.getConfig(), n.getNodeKey()))
                .toList();
        var edgeViews = edges.stream()
                .map(e -> new AutomationValidator.EdgeView(
                        e.getSourceNode().getId().toString(), e.getSourceHandle(),
                        e.getTargetNode().getId().toString(), e.getTargetHandle()))
                .toList();
        Set<String> constantNames = constantsCodec.readNodes(automation.getConstants()).keySet();

        return automationValidator.validate(automation.getKind(), nodeViews, edgeViews, constantNames);
    }

    /**
     * Enforces the per-mailbox SEND grant when activating a mail-sending automation (#4). If the
     * saved flow contains an outbound-mail node (SEND_EMAIL, or EMAIL_ACTION in REPLY/FORWARD mode),
     * the activating user must hold a SEND grant on every mailbox the automation is bound to.
     * Owner/Admin bypass via {@code allMailboxAccess}; throws {@link AccessDeniedException} otherwise.
     */
    private void requireSendGrantsForActivation(UUID organizationId, UUID actingUserId, Automation automation) {
        if (!flowSendsMail(automation.getId())) {
            return;
        }
        UUID[] targets = automation.getAccountIds();
        if (targets == null || targets.length == 0) {
            return;
        }
        OrgContext ctx = orgContextService.resolve(actingUserId, organizationId.toString());
        for (UUID mailboxId : targets) {
            orgContextService.requireMailbox(ctx, mailboxId, OrgContextService.MailboxAccess.SEND);
        }
    }

    /** True if any saved node sends outbound mail: SEND_EMAIL, or EMAIL_ACTION in REPLY/FORWARD mode. */
    private boolean flowSendsMail(UUID automationId) {
        for (var node : nodeRepository.findByAutomationId(automationId)) {
            NodeType type = node.getNodeType();
            if (type == NodeType.SEND_EMAIL) {
                return true;
            }
            if (type == NodeType.EMAIL_ACTION) {
                String mode = readActionMode(node.getConfig());
                if ("REPLY".equals(mode) || "FORWARD".equals(mode)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Reads the EMAIL_ACTION {@code actionMode} config field (defaults to REPLY, matching the executor). */
    private String readActionMode(String config) {
        if (config == null || config.isBlank()) {
            return "REPLY";
        }
        try {
            JsonNode cfg = objectMapper.readTree(config);
            return cfg.path("actionMode").asText("REPLY");
        } catch (Exception e) {
            return "REPLY";
        }
    }

    private Automation findByOrgAndId(UUID organizationId, UUID automationId) {
        return RepositoryHelper.findOrThrow(automationRepository::findByIdAndOrganizationId, automationId, organizationId, "Automation");
    }

    private void syncTriggerAccountIds(Automation automation, FlowUpdateRequest request) {
        for (var nodeReq : request.nodes()) {
            if ("TRIGGER".equals(nodeReq.nodeType()) && nodeReq.config() != null) {
                try {
                    JsonNode config = objectMapper.readTree(nodeReq.config());
                    JsonNode accountIdsNode = config.get("accountIds");
                    if (accountIdsNode != null && accountIdsNode.isArray()) {
                        List<UUID> ids = new ArrayList<>();
                        for (JsonNode idNode : accountIdsNode) {
                            UUID parsed = UuidUtil.parseOrNull(idNode.asText());
                            if (parsed != null) ids.add(parsed);
                        }
                        automation.setAccountIds(ids.toArray(new UUID[0]));
                        return;
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse trigger account IDs from node config: {}", e.getMessage());
                }
            }
        }
    }

    private AutomationResponse toResponse(Automation a, int nodeCount, int edgeCount) {
        return toResponse(a, nodeCount, edgeCount, 0, 0, 0, null);
    }

    private AutomationResponse toResponse(Automation a, int nodeCount, int edgeCount,
                                           long totalExecutions, long successCount, long failedCount,
                                           String triggerMode) {
        TestModeStatsResponse testModeStats = null;
        if (a.getStatus() == AutomationStatus.TESTING) {
            long total = testModeResultRepository.countByAutomationId(a.getId());
            long correct = testModeResultRepository.countByAutomationIdAndFeedback(
                    a.getId(), com.postwerk.model.enums.TestResultFeedback.CORRECT);
            long incorrect = testModeResultRepository.countByAutomationIdAndFeedback(
                    a.getId(), com.postwerk.model.enums.TestResultFeedback.INCORRECT);
            long pending = testModeResultRepository.countByAutomationIdAndFeedbackIsNull(a.getId());
            long rated = correct + incorrect;
            int accuracy = rated > 0 ? (int) Math.round((double) correct / rated * 100) : 0;
            testModeStats = new TestModeStatsResponse(total, correct, incorrect, pending, accuracy);
        }

        return new AutomationResponse(
                a.getId(), a.getName(), a.getDescription(),
                a.getType().name(), a.getKind().name(), a.getStatus().name(),
                triggerMode,
                a.getColor(),
                nodeCount, edgeCount,
                a.getLastRunAt(),
                totalExecutions, successCount, failedCount,
                a.isLocked(),
                a.getCreatedAt(), a.getUpdatedAt(),
                testModeStats
        );
    }

    private AutomationDetailResponse toDetailResponse(Automation a, List<AutomationNode> nodes, List<AutomationEdge> edges) {
        List<AutomationNodeDto> nodeDtos = nodes.stream()
                .map(n -> new AutomationNodeDto(
                        n.getId(), n.getNodeType().name(), n.getLabel(),
                        n.getPositionX(), n.getPositionY(), n.getConfig(), n.getNodeKey()))
                .toList();

        List<AutomationEdgeDto> edgeDtos = edges.stream()
                .map(e -> new AutomationEdgeDto(
                        e.getId(), e.getSourceNode().getId(), e.getSourceHandle(),
                        e.getTargetNode().getId(), e.getTargetHandle()))
                .toList();

        return new AutomationDetailResponse(
                a.getId(), a.getName(), a.getDescription(),
                a.getType().name(), a.getKind().name(), a.getStatus().name(),
                a.getColor(), a.getFlowData(),
                nodeDtos, edgeDtos, parseConstants(a.getConstants()),
                a.getLastRunAt(), a.isLocked(),
                a.getCreatedAt(), a.getUpdatedAt()
        );
    }

    /**
     * Builds the stored JSON object form ({@code {"NAME":{"value":...,"type":...}}}) from incoming
     * constants. Secret values are encrypted at rest; a blank secret value preserves the previously
     * stored secret (read from {@code existingJson}).
     *
     * @param strict when true, invalid names and duplicates throw; when false (import), they are skipped
     */
    private String buildConstantsJson(List<AutomationConstantDto> constants, String existingJson, boolean strict) {
        if (constants == null) return "{}";
        Map<String, JsonNode> existing = constantsCodec.readNodes(existingJson);
        ObjectNode obj = objectMapper.createObjectNode();
        Set<String> seen = new HashSet<>();
        for (AutomationConstantDto c : constants) {
            String name = c.name() == null ? "" : c.name().trim();
            if (!constantsCodec.isValidName(name)) {
                if (strict) throw new IllegalArgumentException("Invalid constant name: '" + name + "' (allowed: letters, digits, underscore)");
                continue;
            }
            if (!seen.add(name)) {
                if (strict) throw new IllegalArgumentException("Duplicate constant name: " + name);
                continue;
            }
            String type = constantsCodec.normalizeType(c.type());
            obj.set(name, constantsCodec.writeEntry(type, c.description(), c.value(), existing.get(name)));
        }
        return obj.toString();
    }

    /**
     * Parses the stored constants JSON into an ordered list for API responses. Supports both the legacy
     * flat form ({@code {"NAME":"value"}}) and the typed form ({@code {"NAME":{"value":...,"type":...}}}).
     * Secret values are never returned in plaintext: their value is {@code null} and {@code hasValue}
     * signals whether a value is stored.
     */
    private List<AutomationConstantDto> parseConstants(String json) {
        Map<String, JsonNode> nodes = constantsCodec.readNodes(json);
        List<AutomationConstantDto> result = new ArrayList<>();
        for (Map.Entry<String, JsonNode> e : nodes.entrySet()) {
            JsonNode node = e.getValue();
            String type = "text";
            String value;
            String desc = "";
            if (node.isObject()) {
                value = node.path("value").asText("");
                type = constantsCodec.normalizeType(node.path("type").asText("text"));
                desc = node.path("desc").asText("");
            } else {
                value = node.asText("");
            }
            if (AutomationConstantsCodec.SECRET_TYPE.equals(type)) {
                boolean hasValue = value != null && !value.isBlank();
                result.add(new AutomationConstantDto(e.getKey(), null, "secret", hasValue, desc));
            } else {
                result.add(new AutomationConstantDto(e.getKey(), value, type, false, desc));
            }
        }
        return result;
    }

    private AutomationExecutionResponse toExecutionResponse(AutomationExecution e) {
        return new AutomationExecutionResponse(
                e.getId(), e.getStatus().name(), e.getTriggeredAt(),
                e.getCompletedAt(), e.getProcessedCount(), e.getErrorLog()
        );
    }
}
