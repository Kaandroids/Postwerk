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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtractNodeProcessorTest {

    @Mock private ExtractNodeExecutor extractNodeExecutor;

    private ExtractNodeProcessor processor;
    private UUID userId;
    private Email email;

    @BeforeEach
    void setUp() {
        processor = new ExtractNodeProcessor(extractNodeExecutor, new ObjectMapper());
        userId = UUID.randomUUID();
        email = createEmail(UUID.randomUUID());
    }

    @Test
    void getNodeType_returnsExtract() {
        assertThat(processor.getNodeType()).isEqualTo(NodeType.EXTRACT);
    }

    @Test
    void process_successfulExtraction_returnsExtractedStatus() {
        Map<String, Map<String, Object>> results = new LinkedHashMap<>();
        results.put("param_set_1", Map.of("customerName", "John", "orderId", "12345"));

        when(extractNodeExecutor.execute(any(Email.class), any(), eq(userId), any())).thenReturn(results);

        AutomationNode node = buildNode("""
                {"parameterSetIds":["param_set_1"]}
                """);
        ExecutionContext context = new ExecutionContext(email, null, false);

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.EXTRACTED);
        assertThat(result.activeHandles()).contains("param_set_1");
        assertThat(result.detail()).containsKey("extractedValues");
    }

    @Test
    void process_extractionWithError_excludesErrorHandles() {
        Map<String, Map<String, Object>> results = new LinkedHashMap<>();
        results.put("good_set", Map.of("name", "John"));
        results.put("bad_set", Map.of("_error", "AI failed"));

        when(extractNodeExecutor.execute(any(Email.class), any(), eq(userId), any())).thenReturn(results);

        AutomationNode node = buildNode("""
                {"parameterSetIds":["good_set","bad_set"]}
                """);
        ExecutionContext context = new ExecutionContext(email, null, false);

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.EXTRACTED);
        assertThat(result.activeHandles()).contains("good_set");
        assertThat(result.activeHandles()).doesNotContain("bad_set");
    }

    @Test
    void process_executorThrows_returnsError() {
        when(extractNodeExecutor.execute(any(Email.class), any(), eq(userId), any()))
                .thenThrow(new RuntimeException("Gemini API error"));

        AutomationNode node = buildNode("""
                {"parameterSetIds":["set1"]}
                """);
        ExecutionContext context = new ExecutionContext(email, null, false);

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.ERROR);
        assertThat((String) result.detail().get("error")).contains("Gemini API error");
    }

    @Test
    void process_invalidConfig_returnsError() {
        AutomationNode node = buildNode("invalid json");
        ExecutionContext context = new ExecutionContext(email, null, false);

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.ERROR);
    }

    private AutomationNode buildNode(String config) {
        return AutomationNode.builder()
                .id(UUID.randomUUID())
                .nodeType(NodeType.EXTRACT)
                .label("Extract")
                .config(config)
                .positionX(0.0).positionY(0.0)
                .createdAt(Instant.now())
                .build();
    }
}
