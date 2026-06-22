package com.postwerk.service;

import com.postwerk.model.Automation;
import com.postwerk.model.AutomationNode;
import com.postwerk.model.WebhookEndpoint;
import com.postwerk.model.enums.NodeType;
import com.postwerk.repository.AutomationNodeRepository;
import com.postwerk.repository.WebhookEndpointRepository;
import com.postwerk.util.TokenGenerator;
import com.postwerk.util.UuidUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Synchronizes inbound webhook endpoints with the current set of saved TRIGGER nodes.
 *
 * <p>Extracted from {@code AutomationServiceImpl} to isolate webhook-endpoint reconciliation
 * from automation CRUD orchestration. Behaviour is unchanged from the original inline helpers.</p>
 *
 * @since 1.0
 */
@Component
public class WebhookEndpointReconciler {

    private static final Logger log = LoggerFactory.getLogger(WebhookEndpointReconciler.class);

    private final WebhookEndpointRepository webhookEndpointRepository;
    private final QuotaService quotaService;
    private final AutomationNodeRepository nodeRepository;
    private final ObjectMapper objectMapper;

    public WebhookEndpointReconciler(WebhookEndpointRepository webhookEndpointRepository,
                                     QuotaService quotaService,
                                     AutomationNodeRepository nodeRepository,
                                     ObjectMapper objectMapper) {
        this.webhookEndpointRepository = webhookEndpointRepository;
        this.quotaService = quotaService;
        this.nodeRepository = nodeRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Synchronizes inbound webhook endpoints with the current set of saved TRIGGER nodes.
     *
     * <p>Because {@code updateFlow} deletes and recreates nodes with fresh UUIDs on every save,
     * the stable reference key lives in the node config ({@code webhookEndpointId} + {@code webhookToken}).
     * For each WEBHOOK-mode trigger: an existing endpoint is re-pointed to the new node id, or a new one
     * is created (subject to the inbound webhook quota) and its id/token written back into the node config.
     * Endpoints of this automation that are no longer referenced get deactivated.</p>
     */
    public void reconcile(Automation automation, List<AutomationNode> savedNodes) {
        Set<UUID> activeEndpointIds = new HashSet<>();

        for (AutomationNode node : savedNodes) {
            if (node.getNodeType() != NodeType.TRIGGER) continue;

            JsonNode config;
            try {
                config = objectMapper.readTree(node.getConfig() != null ? node.getConfig() : "{}");
            } catch (Exception e) {
                log.warn("Failed to parse trigger config during webhook reconcile: {}", e.getMessage());
                continue;
            }

            String mode = config.hasNonNull("triggerMode") ? config.get("triggerMode").asText("EMAIL") : "EMAIL";
            if (!"WEBHOOK".equals(mode)) continue;

            UUID parameterSetId = readUuidField(config, "parameterSetId");
            UUID existingEndpointId = readUuidField(config, "webhookEndpointId");

            WebhookEndpoint endpoint = existingEndpointId != null
                    ? webhookEndpointRepository.findByIdAndUserId(existingEndpointId, automation.getUserId()).orElse(null)
                    : null;

            if (endpoint != null) {
                endpoint.setNodeId(node.getId());
                endpoint.setParameterSetId(parameterSetId);
                endpoint.setActive(true);
                webhookEndpointRepository.save(endpoint);
                activeEndpointIds.add(endpoint.getId());
            } else {
                quotaService.checkInboundWebhookQuota(automation.getOrganizationId());
                WebhookEndpoint created = WebhookEndpoint.builder()
                        .userId(automation.getUserId())
                        .organizationId(automation.getOrganizationId())
                        .automationId(automation.getId())
                        .nodeId(node.getId())
                        .token(TokenGenerator.generate())
                        .authMode("NONE")
                        .parameterSetId(parameterSetId)
                        .active(true)
                        .build();
                created = webhookEndpointRepository.save(created);
                activeEndpointIds.add(created.getId());
                writeWebhookRefToConfig(node, config, created);
            }
        }

        for (WebhookEndpoint existing : webhookEndpointRepository.findByAutomationId(automation.getId())) {
            if (existing.isActive() && !activeEndpointIds.contains(existing.getId())) {
                existing.setActive(false);
                webhookEndpointRepository.save(existing);
            }
        }
    }

    private void writeWebhookRefToConfig(AutomationNode node, JsonNode config, WebhookEndpoint endpoint) {
        try {
            ObjectNode mutable = config.isObject()
                    ? (ObjectNode) config
                    : objectMapper.createObjectNode();
            mutable.put("webhookEndpointId", endpoint.getId().toString());
            mutable.put("webhookToken", endpoint.getToken());
            node.setConfig(objectMapper.writeValueAsString(mutable));
            nodeRepository.save(node);
        } catch (Exception e) {
            log.warn("Failed to write webhook reference into node config: {}", e.getMessage());
        }
    }

    private UUID readUuidField(JsonNode config, String field) {
        return config.hasNonNull(field) ? UuidUtil.parseOrNull(config.get(field).asText()) : null;
    }
}
