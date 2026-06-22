package com.postwerk.service.executor;

import com.postwerk.model.Automation;
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
import java.util.Map;
import java.util.UUID;

import static com.postwerk.TestFixtures.createAutomation;
import static com.postwerk.TestFixtures.createEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DelayNodeProcessorTest {

    @Mock private DelayNodeExecutor delayNodeExecutor;

    private DelayNodeProcessor processor;
    private UUID userId;

    @BeforeEach
    void setUp() {
        processor = new DelayNodeProcessor(delayNodeExecutor, new ObjectMapper());
        userId = UUID.randomUUID();
    }

    @Test
    void getNodeType_returnsDelay() {
        assertThat(processor.getNodeType()).isEqualTo(NodeType.DELAY);
    }

    @Test
    void process_dryRun_returnsSimulated() {
        Map<String, Object> detail = Map.of("delayMinutes", 30, "reason", "dry-run");
        when(delayNodeExecutor.execute(any(), any(), any(), any(), eq(true))).thenReturn(detail);

        AutomationNode node = buildNode("""
                {"delayMinutes":30}
                """);
        ExecutionContext context = new ExecutionContext(createEmail(UUID.randomUUID()), null, true);

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.SIMULATED);
        assertThat(result.followAllEdges()).isTrue();
    }

    @Test
    void process_realRun_haltsTraversal() {
        Map<String, Object> detail = Map.of("delayMinutes", 30, "scheduledAt", "2026-01-01T00:00:00Z");
        when(delayNodeExecutor.execute(any(), any(), any(), any(), eq(false))).thenReturn(detail);

        AutomationNode node = buildNode("""
                {"delayMinutes":30}
                """);
        Automation automation = createAutomation(userId);
        node.setAutomation(automation);
        ExecutionContext context = new ExecutionContext(createEmail(UUID.randomUUID()), null, false);

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.EXECUTED);
        assertThat(result.haltTraversal()).isTrue();
    }

    @Test
    void process_invalidConfig_returnsError() {
        AutomationNode node = buildNode("invalid json");
        ExecutionContext context = new ExecutionContext(null, null, false);

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.ERROR);
        assertThat(result.detail()).containsKey("error");
    }

    private AutomationNode buildNode(String config) {
        return AutomationNode.builder()
                .id(UUID.randomUUID())
                .nodeType(NodeType.DELAY)
                .label("Delay")
                .config(config)
                .positionX(0.0).positionY(0.0)
                .createdAt(Instant.now())
                .build();
    }
}
