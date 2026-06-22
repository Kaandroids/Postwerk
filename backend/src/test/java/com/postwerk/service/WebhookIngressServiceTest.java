package com.postwerk.service;

import com.postwerk.config.EncryptionConfig;
import com.postwerk.model.Automation;
import com.postwerk.model.EmailAccount;
import com.postwerk.model.ParameterSet;
import com.postwerk.model.WebhookEndpoint;
import com.postwerk.model.enums.AutomationStatus;
import com.postwerk.repository.AutomationRepository;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.ParameterSetRepository;
import com.postwerk.repository.WebhookEndpointRepository;
import com.postwerk.service.impl.AutomationExecutorServiceImpl;
import com.postwerk.service.impl.WebhookIngressServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static com.postwerk.TestFixtures.createAutomation;
import static com.postwerk.TestFixtures.createEmailAccount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookIngressServiceTest {

    @Mock private WebhookEndpointRepository endpointRepository;
    @Mock private AutomationRepository automationRepository;
    @Mock private ParameterSetRepository parameterSetRepository;
    @Mock private EmailAccountRepository emailAccountRepository;
    @Mock private AutomationExecutorServiceImpl executorService;
    @Mock private EncryptionConfig encryptionConfig;

    private WebhookIngressServiceImpl service;

    private UUID userId;
    private EmailAccount account;
    private Automation automation;
    private UUID nodeId;

    private static final UnaryOperator<String> NO_HEADERS = name -> null;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new WebhookIngressServiceImpl(endpointRepository, automationRepository,
                parameterSetRepository, emailAccountRepository, executorService,
                encryptionConfig, objectMapper);

        userId = UUID.randomUUID();
        account = createEmailAccount(userId);
        automation = createAutomation(userId);
        automation.setAccountIds(new UUID[]{account.getId()});
        nodeId = UUID.randomUUID();
    }

    private WebhookEndpoint endpoint(String authMode) {
        return WebhookEndpoint.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .automationId(automation.getId())
                .nodeId(nodeId)
                .token("tok-123")
                .authMode(authMode)
                .active(true)
                .triggerCount(0)
                .build();
    }

    // ─── Lookup ───────────────────────────────────────────────────

    @Test
    void ingest_unknownToken_throws404() {
        when(endpointRepository.findByTokenAndActiveTrue("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ingest("missing", "{}", NO_HEADERS))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    // ─── Auth: NONE ───────────────────────────────────────────────

    @Test
    void ingest_authNone_activeAutomation_runsAndReturnsExecutionId() {
        WebhookEndpoint ep = endpoint("NONE");
        UUID executionId = UUID.randomUUID();
        when(endpointRepository.findByTokenAndActiveTrue("tok-123")).thenReturn(Optional.of(ep));
        when(automationRepository.findById(automation.getId())).thenReturn(Optional.of(automation));
        when(emailAccountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(executorService.runInboundWebhook(any(), eq(nodeId), any(), any(), any()))
                .thenReturn(executionId);

        var result = service.ingest("tok-123", "{\"orderId\":\"123\"}", NO_HEADERS);

        assertThat(result.accepted()).isTrue();
        assertThat(result.executionId()).isEqualTo(executionId);
        verify(endpointRepository).save(ep);
        assertThat(ep.getTriggerCount()).isEqualTo(1);
        assertThat(ep.getLastTriggeredAt()).isNotNull();
    }

    @Test
    void ingest_inactiveAutomation_isIgnoredButCounted() {
        WebhookEndpoint ep = endpoint("NONE");
        automation.setStatus(AutomationStatus.PAUSED);
        when(endpointRepository.findByTokenAndActiveTrue("tok-123")).thenReturn(Optional.of(ep));
        when(automationRepository.findById(automation.getId())).thenReturn(Optional.of(automation));

        var result = service.ingest("tok-123", "{}", NO_HEADERS);

        assertThat(result.accepted()).isFalse();
        assertThat(result.executionId()).isNull();
        assertThat(ep.getTriggerCount()).isEqualTo(1);
        verify(executorService, never()).runInboundWebhook(any(), any(), any(), any(), any());
    }

    // ─── Auth: API_KEY ────────────────────────────────────────────

    @Test
    void ingest_apiKey_validHeader_passes() {
        WebhookEndpoint ep = endpoint("API_KEY");
        ep.setSigningSecret("enc-secret");
        ep.setAuthHeaderName("X-API-Key");
        when(endpointRepository.findByTokenAndActiveTrue("tok-123")).thenReturn(Optional.of(ep));
        when(encryptionConfig.decrypt("enc-secret")).thenReturn("plain-secret");
        when(automationRepository.findById(automation.getId())).thenReturn(Optional.of(automation));
        when(emailAccountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(executorService.runInboundWebhook(any(), any(), any(), any(), any()))
                .thenReturn(UUID.randomUUID());

        UnaryOperator<String> headers = name -> "X-API-Key".equals(name) ? "plain-secret" : null;

        var result = service.ingest("tok-123", "{}", headers);

        assertThat(result.accepted()).isTrue();
    }

    @Test
    void ingest_apiKey_invalidHeader_throws401() {
        WebhookEndpoint ep = endpoint("API_KEY");
        ep.setSigningSecret("enc-secret");
        when(endpointRepository.findByTokenAndActiveTrue("tok-123")).thenReturn(Optional.of(ep));
        when(encryptionConfig.decrypt("enc-secret")).thenReturn("plain-secret");

        UnaryOperator<String> headers = name -> "X-API-Key".equals(name) ? "wrong" : null;

        assertThatThrownBy(() -> service.ingest("tok-123", "{}", headers))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
        verify(executorService, never()).runInboundWebhook(any(), any(), any(), any(), any());
    }

    @Test
    void ingest_apiKey_missingHeader_throws401() {
        WebhookEndpoint ep = endpoint("API_KEY");
        ep.setSigningSecret("enc-secret");
        when(endpointRepository.findByTokenAndActiveTrue("tok-123")).thenReturn(Optional.of(ep));
        when(encryptionConfig.decrypt("enc-secret")).thenReturn("plain-secret");

        assertThatThrownBy(() -> service.ingest("tok-123", "{}", NO_HEADERS))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    // ─── Auth: HMAC ───────────────────────────────────────────────

    @Test
    void ingest_hmac_validSignature_passes() {
        WebhookEndpoint ep = endpoint("HMAC");
        ep.setSigningSecret("enc-secret");
        String body = "{\"orderId\":\"123\"}";
        String signature = hmacSha256("plain-secret", body);

        when(endpointRepository.findByTokenAndActiveTrue("tok-123")).thenReturn(Optional.of(ep));
        when(encryptionConfig.decrypt("enc-secret")).thenReturn("plain-secret");
        when(automationRepository.findById(automation.getId())).thenReturn(Optional.of(automation));
        when(emailAccountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(executorService.runInboundWebhook(any(), any(), any(), any(), any()))
                .thenReturn(UUID.randomUUID());

        UnaryOperator<String> headers = name -> "X-Postwerk-Signature".equals(name) ? signature : null;

        var result = service.ingest("tok-123", body, headers);

        assertThat(result.accepted()).isTrue();
    }

    @Test
    void ingest_hmac_invalidSignature_throws401() {
        WebhookEndpoint ep = endpoint("HMAC");
        ep.setSigningSecret("enc-secret");
        when(endpointRepository.findByTokenAndActiveTrue("tok-123")).thenReturn(Optional.of(ep));
        when(encryptionConfig.decrypt("enc-secret")).thenReturn("plain-secret");

        UnaryOperator<String> headers = name -> "X-Postwerk-Signature".equals(name) ? "deadbeef" : null;

        assertThatThrownBy(() -> service.ingest("tok-123", "{}", headers))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    void ingest_hmac_missingSignature_throws401() {
        WebhookEndpoint ep = endpoint("HMAC");
        ep.setSigningSecret("enc-secret");
        when(endpointRepository.findByTokenAndActiveTrue("tok-123")).thenReturn(Optional.of(ep));
        when(encryptionConfig.decrypt("enc-secret")).thenReturn("plain-secret");

        assertThatThrownBy(() -> service.ingest("tok-123", "{}", NO_HEADERS))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    // ─── Payload mapping ──────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void ingest_noParameterSet_mapsAllTopLevelKeys() {
        WebhookEndpoint ep = endpoint("NONE");
        when(endpointRepository.findByTokenAndActiveTrue("tok-123")).thenReturn(Optional.of(ep));
        when(automationRepository.findById(automation.getId())).thenReturn(Optional.of(automation));
        when(emailAccountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(executorService.runInboundWebhook(any(), any(), any(), any(), any()))
                .thenReturn(UUID.randomUUID());

        service.ingest("tok-123", "{\"orderId\":\"123\",\"amount\":42}", NO_HEADERS);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(executorService).runInboundWebhook(any(), any(), any(), any(), captor.capture());
        Map<String, Object> vars = captor.getValue();
        assertThat(vars).containsEntry("trigger.orderId", "123");
        assertThat(vars).containsEntry("trigger.amount", 42L);
        assertThat(vars).containsKey("trigger.body");
        assertThat(vars).containsKey("trigger.receivedAt");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingest_withParameterSet_mapsOnlyDefinedFields() {
        WebhookEndpoint ep = endpoint("NONE");
        UUID paramSetId = UUID.randomUUID();
        ep.setParameterSetId(paramSetId);
        ParameterSet set = ParameterSet.builder()
                .id(paramSetId)
                .userId(userId)
                .name("Order fields")
                .parameters("[{\"name\":\"orderId\",\"type\":\"STRING\",\"description\":\"\",\"isList\":false,\"required\":true}]")
                .build();

        when(endpointRepository.findByTokenAndActiveTrue("tok-123")).thenReturn(Optional.of(ep));
        when(parameterSetRepository.findById(paramSetId)).thenReturn(Optional.of(set));
        when(automationRepository.findById(automation.getId())).thenReturn(Optional.of(automation));
        when(emailAccountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(executorService.runInboundWebhook(any(), any(), any(), any(), any()))
                .thenReturn(UUID.randomUUID());

        service.ingest("tok-123", "{\"orderId\":\"123\",\"secret\":\"ignored\"}", NO_HEADERS);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(executorService).runInboundWebhook(any(), any(), any(), any(), captor.capture());
        Map<String, Object> vars = captor.getValue();
        assertThat(vars).containsEntry("trigger.orderId", "123");
        assertThat(vars).doesNotContainKey("trigger.secret");
    }

    // ─── Account resolution ───────────────────────────────────────

    @Test
    void ingest_noAccountConfigured_runsAccountLess() {
        WebhookEndpoint ep = endpoint("NONE");
        automation.setAccountIds(new UUID[0]);
        UUID executionId = UUID.randomUUID();
        when(endpointRepository.findByTokenAndActiveTrue("tok-123")).thenReturn(Optional.of(ep));
        when(automationRepository.findById(automation.getId())).thenReturn(Optional.of(automation));
        when(executorService.runInboundWebhook(any(), eq(nodeId), isNull(), isNull(), any()))
                .thenReturn(executionId);

        var result = service.ingest("tok-123", "{\"orderId\":\"123\"}", NO_HEADERS);

        assertThat(result.accepted()).isTrue();
        assertThat(result.executionId()).isEqualTo(executionId);
        // Account-less run: no account and no synthetic email persisted
        verify(executorService).runInboundWebhook(any(), eq(nodeId), isNull(), isNull(), any());
        verify(emailAccountRepository, never()).findById(any());
    }

    @Test
    void ingest_accountConfiguredButMissing_throws422() {
        WebhookEndpoint ep = endpoint("NONE");
        when(endpointRepository.findByTokenAndActiveTrue("tok-123")).thenReturn(Optional.of(ep));
        when(automationRepository.findById(automation.getId())).thenReturn(Optional.of(automation));
        when(emailAccountRepository.findById(account.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ingest("tok-123", "{}", NO_HEADERS))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("422");
        verify(executorService, never()).runInboundWebhook(any(), any(), any(), any(), any());
    }

    private static String hmacSha256(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
