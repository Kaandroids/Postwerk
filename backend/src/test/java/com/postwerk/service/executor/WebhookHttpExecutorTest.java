package com.postwerk.service.executor;

import com.postwerk.config.EncryptionConfig;
import com.postwerk.service.QuotaService;
import com.postwerk.service.SecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WebhookHttpExecutor} covering the behaviors that do NOT require a real HTTP
 * call: the dry-run short-circuit (no quota/rate/network side effects), the SSRF guard on the
 * resolved URL ({@link com.postwerk.util.WebhookUrlValidator}), and the per-user rate limiter. The
 * real request/retry/parse path is left to integration tests.
 */
@ExtendWith(MockitoExtension.class)
class WebhookHttpExecutorTest {

    @Mock private WebhookResponseExtractor responseExtractor;
    @Mock private EncryptionConfig encryptionConfig;
    @Mock private QuotaService quotaService;
    @Mock private SecretService secretService;
    @Mock private VariableResolver variableResolver;
    @Mock private ExecutionContext context;

    @InjectMocks
    private WebhookHttpExecutor executor;

    private final ObjectMapper mapper = new ObjectMapper();
    private UUID userId;
    private final UUID nodeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Fresh user per test → the static per-user rate counter starts clean (no cross-test bleed).
        userId = UUID.randomUUID();
    }

    private JsonNode config(String url) throws Exception {
        return mapper.readTree("{\"url\":\"" + url + "\",\"method\":\"POST\"}");
    }

    @Test
    void dryRun_returnsSimulatedResult_withoutQuotaOrRate() throws Exception {
        when(context.isDryRun()).thenReturn(true);
        when(variableResolver.resolveUrlSafe(any(), any())).thenReturn("https://api.example.com/hook");
        when(variableResolver.resolve(any(), any())).thenReturn("");
        when(responseExtractor.match(any(), eq(200)))
                .thenReturn(new WebhookResponseExtractor.Match("success", null));

        var result = executor.execute(config("https://api.example.com/hook"), context, userId, nodeId);

        assertThat(result.simulated()).isTrue();
        assertThat(result.success()).isTrue();
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.handle()).isEqualTo("success");
        // A dry-run must not touch quota, secrets, or encryption (no real side effects).
        verifyNoInteractions(quotaService, secretService, encryptionConfig);
    }

    @Test
    void liveCall_toBlockedUrl_isRejectedBySsrf() throws Exception {
        when(context.isDryRun()).thenReturn(false);
        when(variableResolver.resolveUrlSafe(any(), any())).thenReturn("http://127.0.0.1/hook");
        when(variableResolver.resolve(any(), any())).thenReturn("");

        // SSRF guard resolves the URL host (loopback) and rejects before any HTTP call is made.
        assertThatThrownBy(() -> executor.execute(config("http://127.0.0.1/hook"), context, userId, nodeId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exceedingPerUserRateLimit_throws() throws Exception {
        when(context.isDryRun()).thenReturn(false);
        when(variableResolver.resolveUrlSafe(any(), any())).thenReturn("http://127.0.0.1/hook");
        when(variableResolver.resolve(any(), any())).thenReturn("");
        JsonNode cfg = config("http://127.0.0.1/hook");

        // 60 calls are within the per-minute cap; each still fails the SSRF guard (blocked host), but
        // the rate counter is incremented before that check, so it climbs to the limit.
        for (int i = 0; i < 60; i++) {
            assertThatThrownBy(() -> executor.execute(cfg, context, userId, nodeId))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        // The 61st call trips the rate limiter, which fires before the SSRF guard is even reached.
        assertThatThrownBy(() -> executor.execute(cfg, context, userId, nodeId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rate limit");
    }
}
