package com.postwerk.service.impl;

import com.postwerk.config.EncryptionConfig;
import com.postwerk.dto.GeneratedSecretResponse;
import com.postwerk.dto.WebhookAuthRequest;
import com.postwerk.dto.WebhookEndpointResponse;
import com.postwerk.model.AutomationNode;
import com.postwerk.model.WebhookEndpoint;
import com.postwerk.repository.AutomationNodeRepository;
import com.postwerk.repository.WebhookEndpointRepository;
import com.postwerk.service.WebhookEndpointService;
import com.postwerk.util.RepositoryHelper;
import com.postwerk.util.TokenGenerator;
import com.postwerk.util.WebhookConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Default implementation of {@link WebhookEndpointService}.
 *
 * @since 1.0
 */
@Service
public class WebhookEndpointServiceImpl implements WebhookEndpointService {

    private static final Logger log = LoggerFactory.getLogger(WebhookEndpointServiceImpl.class);
    private static final int GENERATED_SECRET_BYTES = 24;

    private final WebhookEndpointRepository endpointRepository;
    private final AutomationNodeRepository nodeRepository;
    private final EncryptionConfig encryptionConfig;
    private final ObjectMapper objectMapper;
    private final String publicBaseUrl;

    public WebhookEndpointServiceImpl(WebhookEndpointRepository endpointRepository,
                                      AutomationNodeRepository nodeRepository,
                                      EncryptionConfig encryptionConfig,
                                      ObjectMapper objectMapper,
                                      @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.endpointRepository = endpointRepository;
        this.nodeRepository = nodeRepository;
        this.encryptionConfig = encryptionConfig;
        this.objectMapper = objectMapper;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Override
    @Transactional(readOnly = true)
    public WebhookEndpointResponse get(UUID organizationId, UUID id) {
        return toResponse(findOwned(organizationId, id));
    }

    @Override
    @Transactional
    public WebhookEndpointResponse regenerateToken(UUID organizationId, UUID id) {
        WebhookEndpoint endpoint = findOwned(organizationId, id);
        endpoint.setToken(TokenGenerator.generate());
        endpointRepository.save(endpoint);
        writeTokenToNodeConfig(endpoint);
        return toResponse(endpoint);
    }

    @Override
    @Transactional
    public WebhookEndpointResponse setAuth(UUID organizationId, UUID id, WebhookAuthRequest request) {
        WebhookEndpoint endpoint = findOwned(organizationId, id);
        endpoint.setAuthMode(request.authMode());
        endpoint.setAuthHeaderName(
                "API_KEY".equals(request.authMode())
                        ? WebhookConstants.resolveAuthHeader(request.authHeaderName())
                        : null);

        if ("NONE".equals(request.authMode())) {
            endpoint.setSigningSecret(null);
        } else if (request.signingSecret() != null && !request.signingSecret().isBlank()) {
            endpoint.setSigningSecret(encryptionConfig.encrypt(request.signingSecret()));
        }
        endpointRepository.save(endpoint);
        return toResponse(endpoint);
    }

    @Override
    @Transactional
    public GeneratedSecretResponse generateSecret(UUID organizationId, UUID id) {
        WebhookEndpoint endpoint = findOwned(organizationId, id);
        String secret = TokenGenerator.generate(GENERATED_SECRET_BYTES);
        endpoint.setSigningSecret(encryptionConfig.encrypt(secret));
        endpointRepository.save(endpoint);
        return new GeneratedSecretResponse(secret);
    }

    // ─── Helpers ──────────────────────────────────────────────────

    /** Loads the endpoint scoped to the active organization (#4), not the creating user. */
    private WebhookEndpoint findOwned(UUID organizationId, UUID id) {
        return RepositoryHelper.findOrThrow(endpointRepository::findByIdAndOrganizationId, id, organizationId, "WebhookEndpoint");
    }

    private void writeTokenToNodeConfig(WebhookEndpoint endpoint) {
        AutomationNode node = nodeRepository.findById(endpoint.getNodeId()).orElse(null);
        if (node == null) return;
        try {
            ObjectNode config = objectMapper.readTree(node.getConfig() != null ? node.getConfig() : "{}")
                    instanceof ObjectNode on ? on : objectMapper.createObjectNode();
            config.put("webhookEndpointId", endpoint.getId().toString());
            config.put("webhookToken", endpoint.getToken());
            node.setConfig(objectMapper.writeValueAsString(config));
            nodeRepository.save(node);
        } catch (Exception e) {
            log.warn("Failed to write rotated token into node config: {}", e.getMessage());
        }
    }

    private WebhookEndpointResponse toResponse(WebhookEndpoint e) {
        String url = publicBaseUrl + WebhookConstants.HOOKS_PATH_PREFIX + e.getToken();
        boolean hasSecret = e.getSigningSecret() != null && !e.getSigningSecret().isBlank();
        return new WebhookEndpointResponse(
                e.getId(), e.getAutomationId(), e.getNodeId(),
                e.getToken(), url, e.getAuthMode(), e.getAuthHeaderName(),
                hasSecret, e.isActive(), e.getTriggerCount(), e.getLastTriggeredAt());
    }
}
