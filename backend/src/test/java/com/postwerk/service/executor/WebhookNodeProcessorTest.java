package com.postwerk.service.executor;

import com.postwerk.model.AutomationNode;
import com.postwerk.model.Email;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.model.enums.NodeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.postwerk.TestFixtures.createEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookNodeProcessorTest {

    @Mock private WebhookHttpExecutor webhookHttpExecutor;

    private WebhookNodeProcessor processor;
    private UUID userId;
    private Email email;

    @BeforeEach
    void setUp() {
        // Real extractor with a null repo: only match() (handle routing, no DB) is exercised here.
        WebhookResponseExtractor responseExtractor = new WebhookResponseExtractor(new ObjectMapper(), null);
        processor = new WebhookNodeProcessor(new ObjectMapper(), webhookHttpExecutor, responseExtractor);
        userId = UUID.randomUUID();
        email = createEmail(UUID.randomUUID());
    }

    @Test
    void getNodeType_returnsWebhook() {
        assertThat(processor.getNodeType()).isEqualTo(NodeType.WEBHOOK);
    }

    @Test
    void process_dryRun_returnsSimulatedWithDetails() {
        AutomationNode node = buildNode("""
                {"url":"https://api.example.com/webhook","method":"POST","body":"{\\"email\\":\\"test\\"}"}
                """);
        ExecutionContext context = new ExecutionContext(email, null, true);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("url", "https://api.example.com/webhook");
        detail.put("method", "POST");
        detail.put("reason", "dry-run");
        when(webhookHttpExecutor.execute(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new WebhookHttpExecutor.WebhookCallResult(true, 0, null, detail, true, "resp_0"));

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.SIMULATED);
        assertThat(result.detail()).containsEntry("url", "https://api.example.com/webhook");
        assertThat(result.detail()).containsEntry("method", "POST");
        assertThat(result.detail()).containsEntry("reason", "dry-run");
        assertThat(result.activeHandles()).contains("resp_0");
    }

    @Test
    void process_dryRun_resolvesVariables() {
        AutomationNode node = buildNode("""
                {"url":"https://api.example.com/{{extraction_0.orderId}}","method":"GET","body":""}
                """);
        ExecutionContext context = new ExecutionContext(email, null, true)
                .withVariable("extraction_0.orderId", "12345");

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("url", "https://api.example.com/12345");
        detail.put("method", "GET");
        detail.put("reason", "dry-run");
        when(webhookHttpExecutor.execute(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new WebhookHttpExecutor.WebhookCallResult(true, 0, null, detail, true, "resp_0"));

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.SIMULATED);
        assertThat((String) result.detail().get("url")).contains("12345");
    }

    @Test
    void process_dryRun_resolvesEmailVariables() {
        email.setFromAddress("sender@test.com");
        email.setSubject("Order Confirmed");

        AutomationNode node = buildNode("""
                {"url":"https://api.example.com","method":"POST","body":"from={{email.from}}, subj={{email.subject}}"}
                """);
        ExecutionContext context = new ExecutionContext(email, null, true)
                .withVariable("email.from", "sender@test.com")
                .withVariable("email.subject", "Order Confirmed");

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("url", "https://api.example.com");
        detail.put("method", "POST");
        detail.put("resolvedBody", "from=sender@test.com, subj=Order Confirmed");
        detail.put("reason", "dry-run");
        when(webhookHttpExecutor.execute(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new WebhookHttpExecutor.WebhookCallResult(true, 0, null, detail, true, "resp_0"));

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.SIMULATED);
        assertThat((String) result.detail().get("resolvedBody")).contains("sender@test.com");
        assertThat((String) result.detail().get("resolvedBody")).contains("Order Confirmed");
    }

    @Test
    void process_dryRun_defaultMethodIsPost() {
        AutomationNode node = buildNode("""
                {"url":"https://api.example.com"}
                """);
        ExecutionContext context = new ExecutionContext(email, null, true);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("url", "https://api.example.com");
        detail.put("method", "POST");
        detail.put("reason", "dry-run");
        when(webhookHttpExecutor.execute(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new WebhookHttpExecutor.WebhookCallResult(true, 0, null, detail, true, "resp_0"));

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.detail()).containsEntry("method", "POST");
    }

    @Test
    void process_invalidConfig_returnsError() {
        AutomationNode node = buildNode("not valid json");
        ExecutionContext context = new ExecutionContext(email, null, false);

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.ERROR);
        assertThat(result.detail()).containsKey("error");
    }

    @Test
    void process_liveExecution_routesMatchedResponseHandle() {
        AutomationNode node = buildNode("""
                {"url":"https://api.example.com/webhook","method":"POST"}
                """);
        ExecutionContext context = new ExecutionContext(email, null, false);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("url", "https://api.example.com/webhook");
        detail.put("statusCode", 200);
        Map<String, Object> parsedFields = Map.of("orderId", "ABC123");
        when(webhookHttpExecutor.execute(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new WebhookHttpExecutor.WebhookCallResult(true, 200, parsedFields, detail, false, "resp_0"));

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.EXECUTED);
        assertThat(result.activeHandles()).contains("resp_0");
    }

    @Test
    void process_liveExecution_unmatchedRoute() {
        AutomationNode node = buildNode("""
                {"url":"https://api.example.com/webhook","method":"POST"}
                """);
        ExecutionContext context = new ExecutionContext(email, null, false);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("url", "https://api.example.com/webhook");
        detail.put("statusCode", 500);
        when(webhookHttpExecutor.execute(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new WebhookHttpExecutor.WebhookCallResult(false, 500, null, detail, false, "unmatched"));

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.EXECUTED);
        assertThat(result.activeHandles()).contains("unmatched");
    }

    private AutomationNode buildNode(String config) {
        return AutomationNode.builder()
                .id(UUID.randomUUID())
                .nodeType(NodeType.WEBHOOK)
                .label("Webhook")
                .config(config)
                .positionX(0.0).positionY(0.0)
                .createdAt(Instant.now())
                .build();
    }
}
