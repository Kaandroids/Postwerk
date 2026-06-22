package com.postwerk.service.impl;

import com.postwerk.config.EncryptionConfig;
import com.postwerk.dto.ParameterItemDto;
import com.postwerk.model.Automation;
import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.postwerk.model.ParameterSet;
import com.postwerk.model.WebhookEndpoint;
import com.postwerk.model.enums.AutomationStatus;
import com.postwerk.repository.AutomationRepository;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.ParameterSetRepository;
import com.postwerk.repository.WebhookEndpointRepository;
import com.postwerk.service.AutomationExecutorService;
import com.postwerk.service.WebhookIngressService;
import com.postwerk.util.WebhookConstants;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Default implementation of {@link WebhookIngressService}.
 *
 * @since 1.0
 */
@Service
public class WebhookIngressServiceImpl implements WebhookIngressService {

    private static final Logger log = LoggerFactory.getLogger(WebhookIngressServiceImpl.class);

    private static final int MAX_BODY_CHARS = 10_000;

    private final WebhookEndpointRepository endpointRepository;
    private final AutomationRepository automationRepository;
    private final ParameterSetRepository parameterSetRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final AutomationExecutorService executorService;
    private final EncryptionConfig encryptionConfig;
    private final ObjectMapper objectMapper;

    public WebhookIngressServiceImpl(WebhookEndpointRepository endpointRepository,
                                     AutomationRepository automationRepository,
                                     ParameterSetRepository parameterSetRepository,
                                     EmailAccountRepository emailAccountRepository,
                                     AutomationExecutorService executorService,
                                     EncryptionConfig encryptionConfig,
                                     ObjectMapper objectMapper) {
        this.endpointRepository = endpointRepository;
        this.automationRepository = automationRepository;
        this.parameterSetRepository = parameterSetRepository;
        this.emailAccountRepository = emailAccountRepository;
        this.executorService = executorService;
        this.encryptionConfig = encryptionConfig;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public IngestResult ingest(String token, String rawBody, UnaryOperator<String> headerLookup) {
        WebhookEndpoint endpoint = endpointRepository.findByTokenAndActiveTrue(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Webhook endpoint not found"));

        verifyAuth(endpoint, rawBody, headerLookup);

        Automation automation = automationRepository.findById(endpoint.getAutomationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Automation not found"));

        // Always record the hit, even if the automation is currently inactive
        endpoint.setTriggerCount(endpoint.getTriggerCount() + 1);
        endpoint.setLastTriggeredAt(Instant.now());
        endpointRepository.save(endpoint);

        if (automation.getStatus() != AutomationStatus.ACTIVE) {
            log.debug("Webhook {} hit but automation {} is not ACTIVE — ignoring", token, automation.getId());
            return IngestResult.ignored();
        }

        Map<String, Object> triggerVars = mapPayload(endpoint, rawBody);

        EmailAccount account = resolveAccountOrNull(automation);
        Email syntheticEmail = (account != null) ? buildSyntheticEmail(account, rawBody, automation) : null;

        UUID executionId = executorService.runInboundWebhook(
                automation, endpoint.getNodeId(), account, syntheticEmail, triggerVars);

        return IngestResult.accepted(executionId);
    }

    // ─── Authentication ───────────────────────────────────────────

    private void verifyAuth(WebhookEndpoint endpoint, String rawBody, UnaryOperator<String> headerLookup) {
        String mode = endpoint.getAuthMode() != null ? endpoint.getAuthMode() : "NONE";
        switch (mode) {
            case "API_KEY" -> verifyApiKey(endpoint, headerLookup);
            case "HMAC" -> verifyHmac(endpoint, rawBody, headerLookup);
            default -> { /* NONE — only the unguessable token is required */ }
        }
    }

    private void verifyApiKey(WebhookEndpoint endpoint, UnaryOperator<String> headerLookup) {
        String secret = decryptSecretOrReject(endpoint);
        String headerName = WebhookConstants.resolveAuthHeader(endpoint.getAuthHeaderName());
        String provided = headerLookup.apply(headerName);
        if (provided == null || !constantTimeEquals(provided, secret)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing API key");
        }
    }

    private void verifyHmac(WebhookEndpoint endpoint, String rawBody, UnaryOperator<String> headerLookup) {
        String secret = decryptSecretOrReject(endpoint);
        String provided = headerLookup.apply(WebhookConstants.SIGNATURE_HEADER);
        if (provided == null || provided.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing signature");
        }
        if (provided.startsWith("sha256=")) {
            provided = provided.substring("sha256=".length());
        }
        String expected = computeHmacSha256(secret, rawBody != null ? rawBody : "");
        if (!constantTimeEquals(provided, expected)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid signature");
        }
    }

    private String decryptSecretOrReject(WebhookEndpoint endpoint) {
        if (endpoint.getSigningSecret() == null || endpoint.getSigningSecret().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No signing secret configured");
        }
        return encryptionConfig.decrypt(endpoint.getSigningSecret());
    }

    private String computeHmacSha256(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Signature computation failed");
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    // ─── Payload mapping ──────────────────────────────────────────

    private Map<String, Object> mapPayload(WebhookEndpoint endpoint, String rawBody) {
        Map<String, Object> vars = new LinkedHashMap<>();
        JsonNode body = parseBody(rawBody);

        List<ParameterItemDto> params = loadParameters(endpoint);
        if (params != null && !params.isEmpty()) {
            for (ParameterItemDto param : params) {
                JsonNode field = body.get(param.name());
                vars.put("trigger." + param.name(), field != null ? jsonValueToObject(field) : null);
            }
        } else if (body.isObject()) {
            body.fields().forEachRemaining(entry ->
                    vars.put("trigger." + entry.getKey(), jsonValueToObject(entry.getValue())));
        }

        String capped = rawBody == null ? "" :
                (rawBody.length() > MAX_BODY_CHARS ? rawBody.substring(0, MAX_BODY_CHARS) : rawBody);
        vars.put("trigger.body", capped);
        vars.put("trigger.receivedAt", Instant.now().toString());
        return vars;
    }

    private JsonNode parseBody(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(rawBody);
        } catch (Exception e) {
            log.debug("Webhook body is not valid JSON, treating as empty object: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private List<ParameterItemDto> loadParameters(WebhookEndpoint endpoint) {
        if (endpoint.getParameterSetId() == null) return null;
        ParameterSet set = parameterSetRepository.findById(endpoint.getParameterSetId()).orElse(null);
        if (set == null || set.getParameters() == null) return null;
        try {
            return objectMapper.readValue(set.getParameters(), new TypeReference<List<ParameterItemDto>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse parameter set {} for webhook mapping: {}",
                    endpoint.getParameterSetId(), e.getMessage());
            return null;
        }
    }

    private Object jsonValueToObject(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isTextual()) return node.asText();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isInt() || node.isLong()) return node.asLong();
        if (node.isNumber()) return node.asDouble();
        return node.toString();
    }

    // ─── Context resolution ───────────────────────────────────────

    /**
     * Resolves the email account for an inbound webhook automation.
     *
     * <ul>
     *   <li>No {@code accountIds} configured → {@code null} (account-less API-to-API run).</li>
     *   <li>{@code accountIds} configured and found → the account.</li>
     *   <li>{@code accountIds} configured but not found → HTTP 422 (genuine misconfiguration).</li>
     * </ul>
     */
    private EmailAccount resolveAccountOrNull(Automation automation) {
        UUID[] accountIds = automation.getAccountIds();
        if (accountIds == null || accountIds.length == 0) {
            return null;
        }
        return emailAccountRepository.findById(accountIds[0])
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Automation references an email account that no longer exists"));
    }

    private Email buildSyntheticEmail(EmailAccount account, String rawBody, Automation automation) {
        String body = rawBody == null ? "" :
                (rawBody.length() > MAX_BODY_CHARS ? rawBody.substring(0, MAX_BODY_CHARS) : rawBody);
        Instant now = Instant.now();
        return Email.builder()
                .id(UUID.randomUUID())
                .emailAccountId(account.getId())
                .messageId("webhook-" + UUID.randomUUID())
                .folder("INBOX")
                .subject("Inbound webhook: " + automation.getName())
                .bodyText(body)
                .snippet(body.length() > 100 ? body.substring(0, 100) : body)
                .receivedAt(now)
                .isRead(true)
                .isStarred(false)
                .hasAttachments(false)
                .processed(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
