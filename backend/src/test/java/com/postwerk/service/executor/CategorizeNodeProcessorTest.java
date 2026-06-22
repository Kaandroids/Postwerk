package com.postwerk.service.executor;

import com.postwerk.dto.CategoryCandidate;
import com.postwerk.dto.ClassificationResult;
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
import java.util.List;
import java.util.UUID;

import static com.postwerk.TestFixtures.createEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategorizeNodeProcessorTest {

    @Mock private CategorizeNodeExecutor categorizeNodeExecutor;

    private CategorizeNodeProcessor processor;
    private UUID userId;
    private Email email;

    @BeforeEach
    void setUp() {
        processor = new CategorizeNodeProcessor(categorizeNodeExecutor, new ObjectMapper());
        userId = UUID.randomUUID();
        email = createEmail(UUID.randomUUID());
    }

    @Test
    void getNodeType_returnsCategorize() {
        assertThat(processor.getNodeType()).isEqualTo(NodeType.CATEGORIZE);
    }

    @Test
    void process_accepted_returnsCategorizedStatus() {
        UUID catId = UUID.randomUUID();
        ClassificationResult classResult = new ClassificationResult(catId.toString(), 92, "Matches order pattern");
        CategorizeNodeExecutor.CategorizeResult catResult = new CategorizeNodeExecutor.CategorizeResult(
                "cat_" + catId, classResult, true, 70,
                List.of(new CategoryCandidate(catId, "Orders", "Order emails", null, null)),
                catId, "Orders", "#10b981"
        );
        when(categorizeNodeExecutor.executeDetailed(any(Email.class), any(), eq(userId), anyBoolean(), any()))
                .thenReturn(catResult);

        AutomationNode node = buildNode("""
                {"categoryIds":["%s"],"threshold":70}
                """.formatted(catId));
        ExecutionContext context = new ExecutionContext(email, null, false);

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.CATEGORIZED);
        assertThat(result.detail()).containsEntry("categoryName", "Orders");
        assertThat(result.detail()).containsEntry("confidence", 92);
        assertThat(result.detail()).containsEntry("accepted", true);
    }

    @Test
    void process_notAccepted_returnsNotMatchedStatus() {
        ClassificationResult classResult = new ClassificationResult("uncategorized", 30, "No match");
        CategorizeNodeExecutor.CategorizeResult catResult = new CategorizeNodeExecutor.CategorizeResult(
                "uncategorized", classResult, false, 70,
                List.of(), null, null, null
        );
        when(categorizeNodeExecutor.executeDetailed(any(Email.class), any(), eq(userId), anyBoolean(), any()))
                .thenReturn(catResult);

        AutomationNode node = buildNode("""
                {"categoryIds":[],"threshold":70}
                """);
        ExecutionContext context = new ExecutionContext(email, null, false);

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.NOT_MATCHED);
        assertThat(result.detail()).containsEntry("accepted", false);
    }

    @Test
    void process_executorThrows_returnsError() {
        when(categorizeNodeExecutor.executeDetailed(any(Email.class), any(), eq(userId), anyBoolean(), any()))
                .thenThrow(new RuntimeException("AI service down"));

        AutomationNode node = buildNode("""
                {"categoryIds":[],"threshold":70}
                """);
        ExecutionContext context = new ExecutionContext(email, null, false);

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.ERROR);
        assertThat((String) result.detail().get("error")).contains("AI service down");
    }

    @Test
    void process_invalidConfig_returnsError() {
        AutomationNode node = buildNode("bad json");
        ExecutionContext context = new ExecutionContext(email, null, false);

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.ERROR);
    }

    private AutomationNode buildNode(String config) {
        return AutomationNode.builder()
                .id(UUID.randomUUID())
                .nodeType(NodeType.CATEGORIZE)
                .label("Categorize")
                .config(config)
                .positionX(0.0).positionY(0.0)
                .createdAt(Instant.now())
                .build();
    }
}
