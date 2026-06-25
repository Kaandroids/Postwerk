package com.postwerk.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postwerk.model.Automation;
import com.postwerk.model.AutomationNode;
import com.postwerk.model.WebhookEndpoint;
import com.postwerk.model.enums.NodeType;
import com.postwerk.repository.AutomationNodeRepository;
import com.postwerk.repository.WebhookEndpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WebhookEndpointReconciler}. The repositories and quota service are mocked;
 * a real {@link ObjectMapper} parses the node config so the JSON read/write path is exercised for
 * real. Locks the inbound-webhook reconciliation contract: re-point existing endpoints, create new
 * ones under quota (writing the id/token back into the node config), and deactivate orphans.
 */
@ExtendWith(MockitoExtension.class)
class WebhookEndpointReconcilerTest {

    @Mock private WebhookEndpointRepository webhookEndpointRepository;
    @Mock private QuotaService quotaService;
    @Mock private AutomationNodeRepository nodeRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebhookEndpointReconciler reconciler;

    private final UUID userId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID automationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reconciler = new WebhookEndpointReconciler(
                webhookEndpointRepository, quotaService, nodeRepository, objectMapper);
    }

    private Automation automation() {
        return Automation.builder().id(automationId).userId(userId).organizationId(orgId).build();
    }

    private AutomationNode trigger(String config) {
        return AutomationNode.builder().id(UUID.randomUUID()).nodeType(NodeType.TRIGGER).config(config).build();
    }

    // ── nodes that must NOT touch the webhook machinery ──────────────────

    @Test
    void skipsNonTriggerNode() {
        AutomationNode action = AutomationNode.builder()
                .id(UUID.randomUUID()).nodeType(NodeType.SEND_EMAIL).config("{}").build();
        when(webhookEndpointRepository.findByAutomationId(automationId)).thenReturn(List.of());

        reconciler.reconcile(automation(), List.of(action));

        verify(webhookEndpointRepository, never()).save(any());
        verifyNoInteractions(quotaService, nodeRepository);
    }

    @Test
    void skipsEmailModeTrigger() {
        when(webhookEndpointRepository.findByAutomationId(automationId)).thenReturn(List.of());

        reconciler.reconcile(automation(), List.of(trigger("{\"triggerMode\":\"EMAIL\"}")));

        verify(webhookEndpointRepository, never()).save(any());
        verifyNoInteractions(quotaService, nodeRepository);
    }

    @Test
    void skipsTriggerWithoutMode() {
        when(webhookEndpointRepository.findByAutomationId(automationId)).thenReturn(List.of());

        reconciler.reconcile(automation(), List.of(trigger("{}")));

        verify(webhookEndpointRepository, never()).save(any());
        verifyNoInteractions(quotaService);
    }

    @Test
    void skipsTriggerWithMalformedConfig() {
        when(webhookEndpointRepository.findByAutomationId(automationId)).thenReturn(List.of());

        reconciler.reconcile(automation(), List.of(trigger("not-json")));

        verify(webhookEndpointRepository, never()).save(any());
        verifyNoInteractions(quotaService, nodeRepository);
    }

    // ── re-point an existing endpoint ────────────────────────────────────

    @Test
    void reusesExistingEndpointAndRepointsItToTheNewNode() {
        UUID endpointId = UUID.randomUUID();
        UUID paramSetId = UUID.randomUUID();
        AutomationNode node = trigger("{\"triggerMode\":\"WEBHOOK\",\"webhookEndpointId\":\""
                + endpointId + "\",\"parameterSetId\":\"" + paramSetId + "\"}");
        WebhookEndpoint existing = WebhookEndpoint.builder()
                .id(endpointId).userId(userId).automationId(automationId).active(false).build();

        when(webhookEndpointRepository.findByIdAndUserId(endpointId, userId)).thenReturn(Optional.of(existing));
        when(webhookEndpointRepository.findByAutomationId(automationId)).thenReturn(List.of(existing));

        reconciler.reconcile(automation(), List.of(node));

        assertThat(existing.getNodeId()).isEqualTo(node.getId());
        assertThat(existing.getParameterSetId()).isEqualTo(paramSetId);
        assertThat(existing.isActive()).isTrue();
        verify(webhookEndpointRepository).save(existing);
        verifyNoInteractions(quotaService, nodeRepository); // no creation, no config writeback
    }

    // ── create a brand-new endpoint ──────────────────────────────────────

    @Test
    void createsNewEndpointUnderQuotaAndWritesRefBackIntoConfig() throws Exception {
        AutomationNode node = trigger("{\"triggerMode\":\"WEBHOOK\"}");
        when(webhookEndpointRepository.save(any())).thenAnswer(inv -> {
            WebhookEndpoint e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });
        when(webhookEndpointRepository.findByAutomationId(automationId)).thenReturn(List.of());

        reconciler.reconcile(automation(), List.of(node));

        verify(quotaService).checkInboundWebhookQuota(orgId);

        ArgumentCaptor<WebhookEndpoint> captor = ArgumentCaptor.forClass(WebhookEndpoint.class);
        verify(webhookEndpointRepository).save(captor.capture());
        WebhookEndpoint created = captor.getValue();
        assertThat(created.getUserId()).isEqualTo(userId);
        assertThat(created.getOrganizationId()).isEqualTo(orgId);
        assertThat(created.getAutomationId()).isEqualTo(automationId);
        assertThat(created.getNodeId()).isEqualTo(node.getId());
        assertThat(created.getAuthMode()).isEqualTo("NONE");
        assertThat(created.isActive()).isTrue();
        assertThat(created.getToken()).isNotBlank();

        // the id + token are written back into the node config and the node is re-saved
        verify(nodeRepository).save(node);
        JsonNode written = objectMapper.readTree(node.getConfig());
        assertThat(written.get("webhookEndpointId").asText()).isEqualTo(created.getId().toString());
        assertThat(written.get("webhookToken").asText()).isEqualTo(created.getToken());
    }

    @Test
    void createsNewEndpointWhenReferencedIdNoLongerResolves() {
        UUID staleId = UUID.randomUUID();
        AutomationNode node = trigger("{\"triggerMode\":\"WEBHOOK\",\"webhookEndpointId\":\"" + staleId + "\"}");
        when(webhookEndpointRepository.findByIdAndUserId(staleId, userId)).thenReturn(Optional.empty());
        when(webhookEndpointRepository.save(any())).thenAnswer(inv -> {
            WebhookEndpoint e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });
        when(webhookEndpointRepository.findByAutomationId(automationId)).thenReturn(List.of());

        reconciler.reconcile(automation(), List.of(node));

        verify(quotaService).checkInboundWebhookQuota(orgId);
        verify(webhookEndpointRepository).save(any());
    }

    // ── deactivate orphaned endpoints ────────────────────────────────────

    @Test
    void deactivatesActiveEndpointNoLongerReferenced() {
        WebhookEndpoint orphan = WebhookEndpoint.builder()
                .id(UUID.randomUUID()).automationId(automationId).active(true).build();
        when(webhookEndpointRepository.findByAutomationId(automationId)).thenReturn(List.of(orphan));

        reconciler.reconcile(automation(), List.of()); // no trigger nodes -> nothing active

        assertThat(orphan.isActive()).isFalse();
        verify(webhookEndpointRepository).save(orphan);
        verifyNoInteractions(quotaService, nodeRepository);
    }

    @Test
    void leavesAlreadyInactiveEndpointUntouched() {
        WebhookEndpoint inactive = WebhookEndpoint.builder()
                .id(UUID.randomUUID()).automationId(automationId).active(false).build();
        when(webhookEndpointRepository.findByAutomationId(automationId)).thenReturn(List.of(inactive));

        reconciler.reconcile(automation(), List.of());

        verify(webhookEndpointRepository, never()).save(any());
    }

    // ── quota enforcement ────────────────────────────────────────────────

    @Test
    void propagatesQuotaViolationWithoutCreatingEndpoint() {
        AutomationNode node = trigger("{\"triggerMode\":\"WEBHOOK\"}");
        doThrow(new IllegalStateException("inbound webhook quota exceeded"))
                .when(quotaService).checkInboundWebhookQuota(eq(orgId));

        assertThatThrownBy(() -> reconciler.reconcile(automation(), List.of(node)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("quota");

        verify(webhookEndpointRepository, never()).save(any());
    }
}
