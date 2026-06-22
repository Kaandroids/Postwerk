package com.postwerk.service.executor;

import com.postwerk.config.EncryptionConfig;
import com.postwerk.service.QuotaService;
import com.postwerk.service.SecretService;
import com.postwerk.util.NodeConfigReader;
import com.postwerk.util.WebhookConstants;
import com.postwerk.util.WebhookUrlValidator;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared HTTP execution logic for WEBHOOK action nodes and TRIGGER webhook mode.
 * Handles URL building, SSRF validation, auth resolution, retry logic, and response parsing.
 *
 * @since 1.0
 */
@Component
public class WebhookHttpExecutor {

    private static final Logger log = LoggerFactory.getLogger(WebhookHttpExecutor.class);
    private static final int MAX_RESPONSE_SIZE = 32 * 1024;
    private static final int DEFAULT_TIMEOUT = 30;
    private static final int MAX_RETRY_COUNT = 3;
    private static final long MAX_RETRY_DELAY_MS = 30000;
    private static final int MAX_WEBHOOKS_PER_MINUTE = 60;
    private static final long RATE_WINDOW_MS = 60_000;
    /** When the per-user counter map grows past this, sweep out entries whose window has expired. */
    private static final int RATE_MAP_PRUNE_THRESHOLD = 1000;

    private final WebhookResponseExtractor responseExtractor;
    private final EncryptionConfig encryptionConfig;
    private final QuotaService quotaService;
    private final SecretService secretService;
    private final VariableResolver variableResolver;
    private final RestClient restClient;

    private static final Map<UUID, long[]> userWebhookCounts = new ConcurrentHashMap<>();

    public WebhookHttpExecutor(WebhookResponseExtractor responseExtractor,
                               EncryptionConfig encryptionConfig,
                               QuotaService quotaService,
                               SecretService secretService,
                               VariableResolver variableResolver) {
        this.responseExtractor = responseExtractor;
        this.encryptionConfig = encryptionConfig;
        this.quotaService = quotaService;
        this.secretService = secretService;
        this.variableResolver = variableResolver;

        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, "Postwerk-Webhook/1.0")
                .build();
    }

    /**
     * Execute an HTTP call based on the given config JSON node.
     *
     * @param config   JSON config containing url, method, headers, body, auth, retry fields
     * @param context  execution context for variable resolution
     * @param userId   user ID for quota/rate limiting
     * @param nodeId   node ID for variable namespacing
     * @return result containing status, response data, and success/failure indication
     */
    public WebhookCallResult execute(JsonNode config, ExecutionContext context, UUID userId, UUID nodeId) {
        return execute(config, context, userId, nodeId, false);
    }

    /**
     * Execute an HTTP call, optionally forcing a real call during a dry-run.
     *
     * @param forceLive when {@code true}, performs the real HTTP call even in a dry-run
     *                  (test "LIVE" switch), bypassing the simulate short-circuit
     */
    public WebhookCallResult execute(JsonNode config, ExecutionContext context, UUID userId, UUID nodeId, boolean forceLive) {
        String rawUrl = NodeConfigReader.text(config, "url");
        String method = NodeConfigReader.text(config, "method", "POST");
        String rawBody = NodeConfigReader.text(config, "body");
        String authType = NodeConfigReader.text(config, "authType", "NONE");
        int timeout = NodeConfigReader.integer(config, "timeout", DEFAULT_TIMEOUT);
        timeout = Math.max(1, Math.min(60, timeout));

        int retryCount = Math.min(NodeConfigReader.integer(config, "retryCount", 0), MAX_RETRY_COUNT);
        long retryDelayMs = Math.max(1000, Math.min(NodeConfigReader.longValue(config, "retryDelayMs", 3000), MAX_RETRY_DELAY_MS));
        List<String> retryOn = new ArrayList<>();
        if (config.has("retryOn") && config.get("retryOn").isArray()) {
            for (JsonNode r : config.get("retryOn")) {
                retryOn.add(r.asText());
            }
        }

        String resolvedUrl = variableResolver.resolveUrlSafe(rawUrl, context);
        String resolvedBody = variableResolver.resolve(rawBody, context);

        JsonNode responseSchemas = config.get("responseSchemas");
        UUID organizationId = context.getOrganizationId();

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("url", resolvedUrl);
        detail.put("method", method);

        if (context.isDryRun() && !forceLive) {
            detail.put("resolvedBody", resolvedBody);
            detail.put("reason", "dry-run");
            // Route the simulated happy path (HTTP 200) through the matching response branch.
            return new WebhookCallResult(true, 200, null, detail, true,
                    responseExtractor.match(responseSchemas, 200).handle());
        }

        quotaService.checkWebhookEnabled(context.getOrganizationId());
        checkWebhookRate(userId);

        InetAddress resolvedAddress = WebhookUrlValidator.validate(resolvedUrl);
        URI originalUri = URI.create(resolvedUrl);
        String host = originalUri.getHost();
        String connectionUrl = buildConnectionUrl(originalUri, resolvedAddress);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.HOST, host + (originalUri.getPort() > 0 ? ":" + originalUri.getPort() : ""));

        if (config.has("headers") && config.get("headers").isArray()) {
            for (JsonNode h : config.get("headers")) {
                String key = NodeConfigReader.text(h, "key");
                String val = h.has("value") ? variableResolver.resolve(h.get("value").asText(), context) : "";
                if (!key.isBlank() && !containsCRLF(key) && !containsCRLF(val)) {
                    headers.add(key, val);
                }
            }
        }

        applyAuth(headers, config, authType, context.getOrganizationId(), userId);

        HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());
        int totalAttempts = retryCount + 1;

        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            if (attempt > 1) {
                long delay = Math.min(retryDelayMs * (1L << (attempt - 2)), MAX_RETRY_DELAY_MS);
                try { Thread.sleep(delay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
            try {
                var requestSpec = restClient.method(httpMethod)
                        .uri(connectionUrl)
                        .headers(h -> h.addAll(headers));

                if (hasBody(method) && !resolvedBody.isBlank()) {
                    requestSpec.body(resolvedBody);
                }

                var response = requestSpec.exchange((req, res) -> {
                    int statusCode = res.getStatusCode().value();
                    byte[] bodyBytes;
                    try (var is = res.getBody()) {
                        bodyBytes = is.readNBytes(MAX_RESPONSE_SIZE + 1);
                    }
                    boolean truncated = bodyBytes.length > MAX_RESPONSE_SIZE;
                    int readSize = Math.min(bodyBytes.length, MAX_RESPONSE_SIZE);
                    String body = new String(bodyBytes, 0, readSize, StandardCharsets.UTF_8);
                    return new RawResponse(statusCode, body, truncated);
                }, false);

                int statusCode = response.statusCode();

                if (statusCode >= 500 && attempt < totalAttempts && shouldRetry(statusCode, retryOn)) {
                    continue;
                }
                if (statusCode == 429 && attempt < totalAttempts && retryOn.contains("429")) {
                    continue;
                }

                String responseBody = response.body();
                detail.put("statusCode", statusCode);
                detail.put("responseSize", responseBody != null ? responseBody.length() : 0);
                detail.put("success", statusCode >= 200 && statusCode < 400);
                detail.put("attempts", attempt);
                if (response.truncated()) {
                    detail.put("responseTruncated", true);
                }

                // Expose declared response fields (Model B: ParameterSet-driven) and route to the
                // matching response branch. Without a matching ParameterSet only statusCode + raw
                // body are exposed; ad-hoc needs use EXTRACT.
                return buildResult(responseSchemas, statusCode, responseBody, detail, organizationId);

            } catch (HttpServerErrorException e) {
                if (attempt < totalAttempts && shouldRetry(e.getStatusCode().value(), retryOn)) {
                    continue;
                }
                detail.put("error", "Server error");
                detail.put("attempts", attempt);
                // Still expose statusCode + body (+ any matching error schema) so the unmatched/error
                // branch can report the failed status (e.g. {{http_x.statusCode}}).
                return buildResult(responseSchemas, e.getStatusCode().value(), e.getResponseBodyAsString(), detail, organizationId);

            } catch (HttpClientErrorException e) {
                int sc = e.getStatusCode().value();
                if (attempt < totalAttempts && sc == 429 && retryOn.contains("429")) {
                    continue;
                }
                detail.put("error", "Client error");
                detail.put("attempts", attempt);
                return buildResult(responseSchemas, sc, e.getResponseBodyAsString(), detail, organizationId);

            } catch (Exception e) {
                if (attempt < totalAttempts && retryOn.contains("network_error")) {
                    continue;
                }
                log.error("Webhook execution failed for node {}: {}", nodeId, e.getMessage());
                detail.put("error", "Webhook execution failed");
                detail.put("attempts", attempt);
                // No HTTP response (status 0): routes to the unmatched branch, still exposes statusCode=0.
                return buildResult(responseSchemas, 0, "", detail, organizationId);
            }
        }

        detail.put("error", "All retry attempts exhausted");
        detail.put("attempts", totalAttempts);
        return buildResult(responseSchemas, 0, "", detail, organizationId);
    }

    /**
     * Builds a result for a completed call: routes to the matching response branch ({@code resp_<i>}
     * or {@code unmatched}), parses the body with that branch's ParameterSet, and ALWAYS exposes
     * {@code statusCode} + {@code body} as {@code http_<node>.*} variables — so failure/unmatched
     * branches can still report the status. Also records {@code statusCode}/{@code success} on the
     * trace {@code detail}.
     */
    private WebhookCallResult buildResult(JsonNode responseSchemas, int statusCode, String body,
                                          Map<String, Object> detail, UUID organizationId) {
        WebhookResponseExtractor.Match m = responseExtractor.match(responseSchemas, statusCode);
        Map<String, Object> parsedFields = new LinkedHashMap<>(
                responseExtractor.extractWithParameterSet(m.parameterSetId(), body, organizationId));
        parsedFields.put("statusCode", statusCode);
        parsedFields.put("body", body != null ? body : "");
        boolean success = statusCode >= 200 && statusCode < 400;
        detail.put("statusCode", statusCode);
        detail.put("success", success);
        return new WebhookCallResult(success, statusCode, parsedFields, detail, false, m.handle());
    }

    private String buildConnectionUrl(URI original, InetAddress resolvedAddress) {
        String ipAddress = resolvedAddress.getHostAddress();
        if (resolvedAddress.getAddress().length == 16) {
            ipAddress = "[" + ipAddress + "]";
        }
        int port = original.getPort();
        String portPart = port > 0 ? ":" + port : "";
        String path = original.getRawPath() != null ? original.getRawPath() : "";
        String query = original.getRawQuery() != null ? "?" + original.getRawQuery() : "";
        String fragment = original.getRawFragment() != null ? "#" + original.getRawFragment() : "";
        return original.getScheme() + "://" + ipAddress + portPart + path + query + fragment;
    }

    private boolean shouldRetry(int statusCode, List<String> retryOn) {
        if (statusCode == 429 && retryOn.contains("429")) return true;
        if (statusCode >= 500 && retryOn.contains("5xx")) return true;
        return false;
    }

    private void applyAuth(HttpHeaders headers, JsonNode config, String authType, UUID organizationId, UUID userId) {
        String authSecretId = NodeConfigReader.text(config, "authSecretId");

        switch (authType.toUpperCase()) {
            case "BEARER" -> {
                String token;
                if (!authSecretId.isBlank()) {
                    token = secretService.resolveSecret(UUID.fromString(authSecretId), organizationId, userId);
                } else {
                    token = NodeConfigReader.text(config, "authToken");
                    if (!token.isBlank()) {
                        try { token = encryptionConfig.decrypt(token); } catch (Exception e) { token = ""; }
                    }
                }
                if (!token.isBlank()) { headers.setBearerAuth(token); }
            }
            case "BASIC" -> {
                String username = NodeConfigReader.text(config, "authUsername");
                String password;
                if (!authSecretId.isBlank()) {
                    password = secretService.resolveSecret(UUID.fromString(authSecretId), organizationId, userId);
                } else {
                    password = NodeConfigReader.text(config, "authPassword");
                    if (!password.isBlank()) {
                        try { password = encryptionConfig.decrypt(password); } catch (Exception e) { password = ""; }
                    }
                }
                if (!username.isBlank()) { headers.setBasicAuth(username, password); }
            }
            case "API_KEY" -> {
                String headerName = NodeConfigReader.text(config, "authHeaderName", WebhookConstants.DEFAULT_AUTH_HEADER);
                if (containsCRLF(headerName)) { headerName = WebhookConstants.DEFAULT_AUTH_HEADER; }
                String token;
                if (!authSecretId.isBlank()) {
                    token = secretService.resolveSecret(UUID.fromString(authSecretId), organizationId, userId);
                } else {
                    token = NodeConfigReader.text(config, "authToken");
                    if (!token.isBlank()) {
                        try { token = encryptionConfig.decrypt(token); } catch (Exception e) { token = ""; }
                    }
                }
                if (!token.isBlank()) { headers.add(headerName, token); }
            }
            default -> { }
        }
    }

    private boolean hasBody(String method) {
        return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method);
    }

    private boolean containsCRLF(String s) {
        return s != null && (s.contains("\r") || s.contains("\n"));
    }

    // NOTE: this rate limiter is per-JVM-instance only (the counter map is not shared across
    // instances). Behind multiple instances the effective limit is per-instance, not global.
    private void checkWebhookRate(UUID userId) {
        long now = System.currentTimeMillis();
        long windowStart = now - RATE_WINDOW_MS;
        long[] counts = userWebhookCounts.compute(userId, (k, v) -> {
            if (v == null) v = new long[]{0, now};
            if (v[1] < windowStart) { v[0] = 0; v[1] = now; }
            v[0]++;
            return v;
        });
        // Opportunistic cleanup: without this the map would grow one entry per distinct user
        // forever. When it gets large, drop entries whose window has already expired (idle users).
        if (userWebhookCounts.size() > RATE_MAP_PRUNE_THRESHOLD) {
            userWebhookCounts.values().removeIf(v -> v[1] < windowStart);
        }
        if (counts[0] > MAX_WEBHOOKS_PER_MINUTE) {
            throw new IllegalStateException("Webhook rate limit exceeded");
        }
    }

    private record RawResponse(int statusCode, String body, boolean truncated) {}

    /**
     * Result of a webhook HTTP call execution.
     */
    public record WebhookCallResult(
            boolean success,
            int statusCode,
            Map<String, Object> parsedFields,
            Map<String, Object> detail,
            boolean simulated,
            String handle
    ) {}
}
