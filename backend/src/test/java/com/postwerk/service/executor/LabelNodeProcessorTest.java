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
import java.util.Map;
import java.util.UUID;

import static com.postwerk.TestFixtures.createEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LabelNodeProcessorTest {

    @Mock private LabelNodeExecutor labelNodeExecutor;

    private LabelNodeProcessor processor;
    private UUID userId;
    private Email email;

    @BeforeEach
    void setUp() {
        processor = new LabelNodeProcessor(labelNodeExecutor, new ObjectMapper());
        userId = UUID.randomUUID();
        email = createEmail(UUID.randomUUID());
    }

    @Test
    void getNodeType_returnsLabel() {
        assertThat(processor.getNodeType()).isEqualTo(NodeType.LABEL);
    }

    @Test
    void process_dryRun_returnsSimulated() {
        Map<String, Object> detail = Map.of("categoryIds", "cat1,cat2", "reason", "dry-run");
        when(labelNodeExecutor.execute(any(Email.class), any(), eq(true))).thenReturn(detail);

        AutomationNode node = buildNode("""
                {"categoryIds":["cat1","cat2"]}
                """);
        ExecutionContext context = new ExecutionContext(email, null, true);

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.SIMULATED);
        assertThat(result.followAllEdges()).isTrue();
    }

    @Test
    void process_realRun_returnsExecuted() {
        Map<String, Object> detail = Map.of("categoryIds", "cat1,cat2");
        when(labelNodeExecutor.execute(any(Email.class), any(), eq(false))).thenReturn(detail);

        AutomationNode node = buildNode("""
                {"categoryIds":["cat1","cat2"]}
                """);
        ExecutionContext context = new ExecutionContext(email, null, false);

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.EXECUTED);
        assertThat(result.followAllEdges()).isTrue();
    }

    @Test
    void process_invalidConfig_returnsError() {
        AutomationNode node = buildNode("invalid json");
        ExecutionContext context = new ExecutionContext(email, null, false);

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.ERROR);
        assertThat(result.detail()).containsKey("error");
    }

    private AutomationNode buildNode(String config) {
        return AutomationNode.builder()
                .id(UUID.randomUUID())
                .nodeType(NodeType.LABEL)
                .label("Label")
                .config(config)
                .positionX(0.0).positionY(0.0)
                .createdAt(Instant.now())
                .build();
    }
}
