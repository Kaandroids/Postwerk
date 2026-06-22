package com.postwerk.service.executor;

import com.postwerk.model.AutomationNode;
import com.postwerk.model.Email;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.model.enums.NodeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.postwerk.TestFixtures.createEmail;
import static org.assertj.core.api.Assertions.assertThat;

class FilterNodeProcessorTest {

    private FilterNodeProcessor processor;
    private Email email;

    @BeforeEach
    void setUp() {
        VariableFilterEvaluator evaluator = new VariableFilterEvaluator(new ObjectMapper());
        processor = new FilterNodeProcessor(evaluator);
        email = createEmail(UUID.randomUUID());
    }

    @Test
    void getNodeType_returnsFilter() {
        assertThat(processor.getNodeType()).isEqualTo(NodeType.FILTER);
    }

    @Test
    void process_matchingFilter_routesToCheckHandle() {
        String config = """
                {"checks":[{"label":"Test","groups":[{"conditions":[{"field":"email.from","operator":"CONTAINS","value":"sender"}]}]}]}
                """;
        AutomationNode node = buildNode(config);
        ExecutionContext context = new ExecutionContext(email, null, false)
                .withVariable("email.from", "sender@example.com");

        NodeProcessorResult result = processor.process(node, context, UUID.randomUUID());

        assertThat(result.status()).isEqualTo(NodeResultStatus.MATCHED);
        assertThat(result.activeHandles()).contains("check_0");
        assertThat(result.detail()).containsEntry("matched", true);
    }

    @Test
    void process_nonMatchingFilter_routesToFallbackHandle() {
        String config = """
                {"checks":[{"label":"Test","groups":[{"conditions":[{"field":"email.from","operator":"EQUALS","value":"nobody@nowhere.com"}]}]}]}
                """;
        AutomationNode node = buildNode(config);
        ExecutionContext context = new ExecutionContext(email, null, false)
                .withVariable("email.from", "sender@example.com");

        NodeProcessorResult result = processor.process(node, context, UUID.randomUUID());

        assertThat(result.status()).isEqualTo(NodeResultStatus.NOT_MATCHED);
        assertThat(result.activeHandles()).containsExactly("fallback");
        assertThat(result.detail()).containsEntry("matched", false);
    }

    @Test
    void process_emptyChecks_routesToFallback() {
        AutomationNode node = buildNode("""
                {"checks":[]}
                """);
        ExecutionContext context = new ExecutionContext(email, null, false);

        NodeProcessorResult result = processor.process(node, context, UUID.randomUUID());

        assertThat(result.status()).isEqualTo(NodeResultStatus.NOT_MATCHED);
        assertThat(result.activeHandles()).containsExactly("fallback");
    }

    private AutomationNode buildNode(String config) {
        return AutomationNode.builder()
                .id(UUID.randomUUID())
                .nodeType(NodeType.FILTER)
                .label("Filter")
                .config(config)
                .positionX(0.0).positionY(0.0)
                .createdAt(Instant.now())
                .build();
    }
}
