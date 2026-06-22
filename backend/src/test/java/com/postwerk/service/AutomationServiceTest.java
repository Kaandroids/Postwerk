package com.postwerk.service;

import com.postwerk.TestFixtures;
import com.postwerk.dto.AutomationRequest;
import com.postwerk.dto.AutomationStatusRequest;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.Automation;
import com.postwerk.model.AuditAction;
import com.postwerk.model.enums.AutomationStatus;
import com.postwerk.repository.*;
import com.postwerk.service.impl.AutomationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutomationServiceTest {

    @Mock private AutomationRepository automationRepository;
    @Mock private AutomationNodeRepository nodeRepository;
    @Mock private AutomationEdgeRepository edgeRepository;
    @Mock private AutomationExecutionRepository executionRepository;
    @Mock private AutomationTestModeResultRepository testModeResultRepository;
    @Mock private AuditService auditService;
    @Mock private ObjectMapper objectMapper;
    @Mock private QuotaService quotaService;
    @Mock private com.postwerk.service.WebhookEndpointReconciler webhookEndpointReconciler;
    @Mock private com.postwerk.repository.MarketplaceListingRepository marketplaceListingRepository;
    @Mock private com.postwerk.service.AutomationConstantsCodec constantsCodec;
    @Mock private com.postwerk.service.AutomationValidator automationValidator;

    @Mock private com.postwerk.service.OrgContextService orgContextService;
    @Mock private com.postwerk.service.AutomationExecutorService executorService;
    @Mock private EmailAccountRepository emailAccountRepository;

    @InjectMocks
    private AutomationServiceImpl service;

    private UUID orgId;
    private UUID userId;
    private Automation automation;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        userId = UUID.randomUUID();
        automation = TestFixtures.createAutomation(userId);
    }

    @Test
    void create_validAutomation_persists() {
        var request = new AutomationRequest("Test Auto", "Description", "#3b82f6", null);
        when(automationRepository.save(any(Automation.class))).thenAnswer(inv -> {
            Automation a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        var response = service.create(orgId, userId, request, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Test Auto");
        verify(auditService).log(eq(userId), eq(AuditAction.AUTOMATION_CREATED), anyString(), anyString());
    }

    @Test
    void update_changesFields() {
        when(automationRepository.findByIdAndOrganizationId(automation.getId(), orgId))
                .thenReturn(Optional.of(automation));
        when(automationRepository.save(any(Automation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(nodeRepository.countByAutomationId(any())).thenReturn(0);
        when(edgeRepository.countByAutomationId(any())).thenReturn(0);

        var request = new AutomationRequest("Updated Name", "Updated desc", "#ef4444", null);
        var response = service.update(orgId, userId, automation.getId(), request, "127.0.0.1");

        assertThat(response.name()).isEqualTo("Updated Name");
    }

    @Test
    void delete_deletesAutomation() {
        when(automationRepository.findByIdAndOrganizationId(automation.getId(), orgId))
                .thenReturn(Optional.of(automation));

        service.delete(orgId, userId, automation.getId(), "127.0.0.1");

        verify(automationRepository).delete(automation);
    }

    @Test
    void delete_publishedAutomation_isBlocked() {
        when(automationRepository.findByIdAndOrganizationId(automation.getId(), orgId))
                .thenReturn(Optional.of(automation));
        when(marketplaceListingRepository.findByAutomationIdAndDeletedAtIsNull(automation.getId()))
                .thenReturn(Optional.of(new com.postwerk.model.MarketplaceListing()));

        assertThatThrownBy(() -> service.delete(orgId, userId, automation.getId(), "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(automationRepository, never()).delete(automation);
    }

    @Test
    void activate_setsStatusActive() {
        automation.setStatus(AutomationStatus.PAUSED);
        when(automationRepository.findByIdAndOrganizationId(automation.getId(), orgId))
                .thenReturn(Optional.of(automation));
        when(automationRepository.save(any(Automation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(nodeRepository.countByAutomationId(any())).thenReturn(0);
        when(edgeRepository.countByAutomationId(any())).thenReturn(0);
        when(nodeRepository.findByAutomationId(any())).thenReturn(List.of());
        when(edgeRepository.findByAutomationId(any())).thenReturn(List.of());
        when(constantsCodec.readNodes(any())).thenReturn(java.util.Map.of());
        when(automationValidator.validate(any(), anyList(), anyList(), anySet()))
                .thenReturn(new com.postwerk.dto.automation.AutomationValidationResult(true, List.of()));

        var response = service.updateStatus(orgId, userId, automation.getId(), new AutomationStatusRequest("ACTIVE"), "127.0.0.1");

        assertThat(automation.getStatus()).isEqualTo(AutomationStatus.ACTIVE);
    }

    @Test
    void activate_withValidationErrors_isBlocked() {
        automation.setStatus(AutomationStatus.PAUSED);
        when(automationRepository.findByIdAndOrganizationId(automation.getId(), orgId))
                .thenReturn(Optional.of(automation));
        when(nodeRepository.findByAutomationId(any())).thenReturn(List.of());
        when(edgeRepository.findByAutomationId(any())).thenReturn(List.of());
        when(constantsCodec.readNodes(any())).thenReturn(java.util.Map.of());
        var issue = com.postwerk.dto.automation.ValidationIssue.error("MISSING_TRIGGER", null, "no trigger");
        when(automationValidator.validate(any(), anyList(), anyList(), anySet()))
                .thenReturn(new com.postwerk.dto.automation.AutomationValidationResult(false, List.of(issue)));

        assertThatThrownBy(() -> service.updateStatus(orgId, userId, automation.getId(),
                new AutomationStatusRequest("ACTIVE"), "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(automation.getStatus()).isEqualTo(AutomationStatus.PAUSED);
    }

    @Test
    void activate_mailSendingFlow_withoutSendGrant_isBlocked() {
        automation.setStatus(AutomationStatus.PAUSED);
        var sendNode = com.postwerk.model.AutomationNode.builder()
                .id(UUID.randomUUID())
                .nodeType(com.postwerk.model.enums.NodeType.SEND_EMAIL)
                .label("Send")
                .config("{}")
                .build();
        when(automationRepository.findByIdAndOrganizationId(automation.getId(), orgId))
                .thenReturn(Optional.of(automation));
        when(nodeRepository.findByAutomationId(any())).thenReturn(List.of(sendNode));
        when(edgeRepository.findByAutomationId(any())).thenReturn(List.of());
        when(constantsCodec.readNodes(any())).thenReturn(java.util.Map.of());
        when(automationValidator.validate(any(), anyList(), anyList(), anySet()))
                .thenReturn(new com.postwerk.dto.automation.AutomationValidationResult(true, List.of()));
        var ctx = new OrgContext(orgId, userId, UUID.randomUUID(),
                com.postwerk.model.enums.OrgRole.MEMBER,
                com.postwerk.model.enums.OrgRole.MEMBER.permissions(), false);
        when(orgContextService.resolve(userId, orgId.toString())).thenReturn(ctx);
        doThrow(new org.springframework.security.access.AccessDeniedException("No SEND access to this mailbox"))
                .when(orgContextService).requireMailbox(eq(ctx), any(),
                        eq(com.postwerk.service.OrgContextService.MailboxAccess.SEND));

        assertThatThrownBy(() -> service.updateStatus(orgId, userId, automation.getId(),
                new AutomationStatusRequest("ACTIVE"), "127.0.0.1"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        assertThat(automation.getStatus()).isEqualTo(AutomationStatus.PAUSED);
        verify(automationRepository, never()).save(any());
    }

    @Test
    void activate_mailSendingFlow_withSendGrant_activates() {
        automation.setStatus(AutomationStatus.PAUSED);
        var sendNode = com.postwerk.model.AutomationNode.builder()
                .id(UUID.randomUUID())
                .nodeType(com.postwerk.model.enums.NodeType.SEND_EMAIL)
                .label("Send")
                .config("{}")
                .build();
        when(automationRepository.findByIdAndOrganizationId(automation.getId(), orgId))
                .thenReturn(Optional.of(automation));
        when(automationRepository.save(any(Automation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(nodeRepository.countByAutomationId(any())).thenReturn(1);
        when(edgeRepository.countByAutomationId(any())).thenReturn(0);
        when(nodeRepository.findByAutomationId(any())).thenReturn(List.of(sendNode));
        when(edgeRepository.findByAutomationId(any())).thenReturn(List.of());
        when(constantsCodec.readNodes(any())).thenReturn(java.util.Map.of());
        when(automationValidator.validate(any(), anyList(), anyList(), anySet()))
                .thenReturn(new com.postwerk.dto.automation.AutomationValidationResult(true, List.of()));
        // Owner bypasses per-mailbox grants (allMailboxAccess); requireMailbox is a no-op here.
        var ctx = new OrgContext(orgId, userId, UUID.randomUUID(),
                com.postwerk.model.enums.OrgRole.OWNER,
                com.postwerk.model.enums.OrgRole.OWNER.permissions(), true);
        when(orgContextService.resolve(userId, orgId.toString())).thenReturn(ctx);

        service.updateStatus(orgId, userId, automation.getId(),
                new AutomationStatusRequest("ACTIVE"), "127.0.0.1");

        assertThat(automation.getStatus()).isEqualTo(AutomationStatus.ACTIVE);
        verify(orgContextService).requireMailbox(eq(ctx), any(),
                eq(com.postwerk.service.OrgContextService.MailboxAccess.SEND));
    }

    @Test
    void deactivate_setsStatusInactive() {
        automation.setStatus(AutomationStatus.ACTIVE);
        when(automationRepository.findByIdAndOrganizationId(automation.getId(), orgId))
                .thenReturn(Optional.of(automation));
        when(automationRepository.save(any(Automation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(nodeRepository.countByAutomationId(any())).thenReturn(0);
        when(edgeRepository.countByAutomationId(any())).thenReturn(0);

        service.updateStatus(orgId, userId, automation.getId(), new AutomationStatusRequest("PAUSED"), "127.0.0.1");

        assertThat(automation.getStatus()).isEqualTo(AutomationStatus.PAUSED);
    }

    // ── Role gates (#4 ladder): activate ⇒ AUTOMATION_ACTIVATE; pause/edit ⇒ AUTOMATION_EDIT ──

    @Test
    void activate_withoutActivatePermission_isBlocked() {
        // An EDITOR builds but can never go live: going ACTIVE requires AUTOMATION_ACTIVATE (Admin/Owner).
        automation.setStatus(AutomationStatus.PAUSED);
        when(automationRepository.findByIdAndOrganizationId(automation.getId(), orgId))
                .thenReturn(Optional.of(automation));
        var ctx = new OrgContext(orgId, userId, UUID.randomUUID(),
                com.postwerk.model.enums.OrgRole.EDITOR,
                com.postwerk.model.enums.OrgRole.EDITOR.permissions(), false);
        when(orgContextService.resolve(userId, orgId.toString())).thenReturn(ctx);
        doThrow(new org.springframework.security.access.AccessDeniedException("Missing permission: AUTOMATION_ACTIVATE"))
                .when(orgContextService).require(ctx, com.postwerk.model.enums.Permission.AUTOMATION_ACTIVATE);

        assertThatThrownBy(() -> service.updateStatus(orgId, userId, automation.getId(),
                new AutomationStatusRequest("ACTIVE"), "127.0.0.1"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        assertThat(automation.getStatus()).isEqualTo(AutomationStatus.PAUSED);
        verify(automationRepository, never()).save(any());
    }

    @Test
    void pause_withoutEditPermission_isBlocked() {
        // A VIEWER cannot change status at all: even pausing requires AUTOMATION_EDIT (Editor+).
        automation.setStatus(AutomationStatus.ACTIVE);
        when(automationRepository.findByIdAndOrganizationId(automation.getId(), orgId))
                .thenReturn(Optional.of(automation));
        var ctx = new OrgContext(orgId, userId, UUID.randomUUID(),
                com.postwerk.model.enums.OrgRole.VIEWER,
                com.postwerk.model.enums.OrgRole.VIEWER.permissions(), false);
        when(orgContextService.resolve(userId, orgId.toString())).thenReturn(ctx);
        doThrow(new org.springframework.security.access.AccessDeniedException("Missing permission: AUTOMATION_EDIT"))
                .when(orgContextService).require(ctx, com.postwerk.model.enums.Permission.AUTOMATION_EDIT);

        assertThatThrownBy(() -> service.updateStatus(orgId, userId, automation.getId(),
                new AutomationStatusRequest("PAUSED"), "127.0.0.1"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        assertThat(automation.getStatus()).isEqualTo(AutomationStatus.ACTIVE);
        verify(automationRepository, never()).save(any());
    }

    @Test
    void updateFlow_withoutEditPermission_isBlocked() {
        // Members/Viewers get a read-only editor; the server enforces AUTOMATION_EDIT on the flow write.
        when(automationRepository.findByIdAndOrganizationId(automation.getId(), orgId))
                .thenReturn(Optional.of(automation));
        var ctx = new OrgContext(orgId, userId, UUID.randomUUID(),
                com.postwerk.model.enums.OrgRole.MEMBER,
                com.postwerk.model.enums.OrgRole.MEMBER.permissions(), false);
        when(orgContextService.resolve(userId, orgId.toString())).thenReturn(ctx);
        doThrow(new org.springframework.security.access.AccessDeniedException("Missing permission: AUTOMATION_EDIT"))
                .when(orgContextService).require(ctx, com.postwerk.model.enums.Permission.AUTOMATION_EDIT);

        var request = new com.postwerk.dto.FlowUpdateRequest(List.of(), List.of(), null);
        assertThatThrownBy(() -> service.updateFlow(orgId, userId, automation.getId(), request, "127.0.0.1"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verify(automationRepository, never()).save(any());
        verify(nodeRepository, never()).deleteByAutomationId(any());
    }

    @Test
    void getById_wrongOrg_throws() {
        UUID otherOrg = UUID.randomUUID();
        when(automationRepository.findByIdAndOrganizationId(automation.getId(), otherOrg))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(otherOrg, automation.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void list_returnsAll() {
        when(automationRepository.findTop200ByOrganizationIdOrderByCreatedAtDesc(orgId)).thenReturn(List.of(automation));
        when(nodeRepository.countByAutomationIds(anyList())).thenReturn(List.<Object[]>of(new Object[]{automation.getId(), 3L}));
        when(edgeRepository.countByAutomationIds(anyList())).thenReturn(List.<Object[]>of(new Object[]{automation.getId(), 2L}));
        when(executionRepository.statsForAutomations(anyList())).thenReturn(List.of());

        var result = service.listByOrg(orgId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nodeCount()).isEqualTo(3);
        assertThat(result.get(0).edgeCount()).isEqualTo(2);
    }

    @Test
    void list_populatesExecutionStats() {
        when(automationRepository.findTop200ByOrganizationIdOrderByCreatedAtDesc(orgId)).thenReturn(List.of(automation));
        when(nodeRepository.countByAutomationIds(anyList())).thenReturn(List.<Object[]>of(new Object[]{automation.getId(), 2L}));
        when(edgeRepository.countByAutomationIds(anyList())).thenReturn(List.<Object[]>of(new Object[]{automation.getId(), 1L}));
        Object[] row = new Object[]{automation.getId(), 50L, 45L, 5L};
        List<Object[]> stats = new ArrayList<>();
        stats.add(row);
        when(executionRepository.statsForAutomations(anyList())).thenReturn(stats);

        var result = service.listByOrg(orgId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).totalExecutions()).isEqualTo(50);
        assertThat(result.get(0).successCount()).isEqualTo(45);
        assertThat(result.get(0).failedCount()).isEqualTo(5);
    }

    @Test
    void list_noExecutions_returnsZeroStats() {
        when(automationRepository.findTop200ByOrganizationIdOrderByCreatedAtDesc(orgId)).thenReturn(List.of(automation));
        when(nodeRepository.countByAutomationIds(anyList())).thenReturn(List.of());
        when(edgeRepository.countByAutomationIds(anyList())).thenReturn(List.of());
        when(executionRepository.statsForAutomations(anyList())).thenReturn(List.of());

        var result = service.listByOrg(orgId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).totalExecutions()).isEqualTo(0);
        assertThat(result.get(0).successCount()).isEqualTo(0);
        assertThat(result.get(0).failedCount()).isEqualTo(0);
    }

    @Test
    void create_returnsZeroStats() {
        var request = new AutomationRequest("New Auto", "Desc", "#3b82f6", null);
        when(automationRepository.save(any(Automation.class))).thenAnswer(inv -> {
            Automation a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        var response = service.create(orgId, userId, request, "127.0.0.1");

        assertThat(response.totalExecutions()).isEqualTo(0);
        assertThat(response.successCount()).isEqualTo(0);
        assertThat(response.failedCount()).isEqualTo(0);
    }

    // ── Manual run ────────────────────────────────────────────────

    /** Delegates readTree to a real mapper so isManualTrigger / triggerMode parsing works in tests. */
    private void stubRealObjectMapper() throws Exception {
        var real = new ObjectMapper();
        when(objectMapper.readTree(anyString())).thenAnswer(inv -> real.readTree((String) inv.getArgument(0)));
    }

    private com.postwerk.model.AutomationNode triggerNode(String triggerMode) {
        return com.postwerk.model.AutomationNode.builder()
                .id(UUID.randomUUID())
                .nodeType(com.postwerk.model.enums.NodeType.TRIGGER)
                .label("Trigger")
                .config("{\"triggerMode\":\"" + triggerMode + "\"}")
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void runManually_manualTrigger_seedsTriggerVarsAndRuns() throws Exception {
        automation.setStatus(AutomationStatus.ACTIVE);
        automation.setAccountIds(new UUID[0]); // account-less run → no synthetic email
        var trigger = triggerNode("MANUAL");
        when(automationRepository.findByIdAndOrganizationId(automation.getId(), orgId))
                .thenReturn(Optional.of(automation));
        when(nodeRepository.findByAutomationId(automation.getId())).thenReturn(List.of(trigger));
        stubRealObjectMapper();

        service.runManually(orgId, userId, automation.getId(), java.util.Map.of("amount", "500"), "127.0.0.1");

        var varsCaptor = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(executorService).runInboundWebhook(eq(automation), eq(trigger.getId()), isNull(), isNull(),
                (java.util.Map<String, Object>) varsCaptor.capture());
        java.util.Map<String, Object> vars = varsCaptor.getValue();
        assertThat(vars).containsEntry("trigger.amount", "500");
        assertThat(vars).containsKey("trigger.receivedAt");
        verify(auditService).log(eq(userId), eq(AuditAction.AUTOMATION_EXECUTED), anyString(), anyString());
    }

    @Test
    void runManually_notActive_isBlocked() {
        automation.setStatus(AutomationStatus.PAUSED);
        when(automationRepository.findByIdAndOrganizationId(automation.getId(), orgId))
                .thenReturn(Optional.of(automation));

        assertThatThrownBy(() -> service.runManually(orgId, userId, automation.getId(),
                java.util.Map.of(), "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(executorService, never()).runInboundWebhook(any(), any(), any(), any(), anyMap());
    }

    @Test
    void runManually_noManualTrigger_isBlocked() throws Exception {
        automation.setStatus(AutomationStatus.ACTIVE);
        when(automationRepository.findByIdAndOrganizationId(automation.getId(), orgId))
                .thenReturn(Optional.of(automation));
        when(nodeRepository.findByAutomationId(automation.getId())).thenReturn(List.of(triggerNode("EMAIL")));
        stubRealObjectMapper();

        assertThatThrownBy(() -> service.runManually(orgId, userId, automation.getId(),
                java.util.Map.of(), "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(executorService, never()).runInboundWebhook(any(), any(), any(), any(), anyMap());
    }
}
