package com.postwerk.service.executor;

import com.postwerk.TestFixtures;
import com.postwerk.model.AutomationNode;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.model.enums.NodeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link ForEachNodeProcessor}: the fan-out contexts it produces from a list source
 * variable (alias binding, index/count helpers, passthrough of other vars, the iteration cap, and
 * the no-source / empty-list guards).
 */
@ExtendWith(MockitoExtension.class)
class ForEachNodeProcessorTest {

    private ForEachNodeProcessor processor;
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        processor = new ForEachNodeProcessor(mapper);
    }

    private AutomationNode node(String configJson) {
        AutomationNode n = mock(AutomationNode.class);
        lenient().when(n.getConfig()).thenReturn(configJson);
        lenient().when(n.getId()).thenReturn(UUID.randomUUID());
        lenient().when(n.getNodeType()).thenReturn(NodeType.FOREACH);
        return n;
    }

    private ExecutionContext ctxWith(Map<String, Object> vars) {
        return new ExecutionContext(TestFixtures.createEmail(UUID.randomUUID()),
                TestFixtures.createEmailAccount(UUID.randomUUID())).withVariables(vars);
    }

    private static Map<String, Object> attachment(String name, String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("contentType", type);
        m.put("size", "10 KB");
        return m;
    }

    @Test
    void noSourceVariable_returnsErrorHalt() {
        NodeProcessorResult result = processor.process(node("{}"), ctxWith(Map.of()), userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.ERROR);
        assertThat(result.haltTraversal()).isTrue();
    }

    @Test
    void emptyOrMissingList_skips_andDoesNotFanOut() {
        NodeProcessorResult missing = processor.process(
                node("{\"sourceVariable\":\"email.attachments\"}"), ctxWith(Map.of()), userId);
        assertThat(missing.status()).isEqualTo(NodeResultStatus.SKIPPED);
        assertThat(missing.fanOutContexts()).isEmpty();
        assertThat(missing.haltTraversal()).isTrue();

        NodeProcessorResult empty = processor.process(
                node("{\"sourceVariable\":\"email.attachments\"}"),
                ctxWith(Map.of("email.attachments", List.of())), userId);
        assertThat(empty.status()).isEqualTo(NodeResultStatus.SKIPPED);
        assertThat(empty.fanOutContexts()).isEmpty();
    }

    @Test
    void listOfMaps_fansOut_bindingItemFieldsIndexAndCount_andPassesOtherVarsThrough() {
        List<Map<String, Object>> atts = List.of(
                attachment("a.pdf", "application/pdf"),
                attachment("b.png", "image/png"));
        ExecutionContext ctx = ctxWith(Map.of("email.attachments", atts, "email.subject", "Hi"));

        NodeProcessorResult result = processor.process(
                node("{\"sourceVariable\":\"email.attachments\"}"), ctx, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.PASSED);
        assertThat(result.fanOutHandle()).isEqualTo("each");
        assertThat(result.fanOutContexts()).hasSize(2);

        ExecutionContext c0 = result.fanOutContexts().get(0);
        assertThat(c0.getVariable("item.name")).isEqualTo("a.pdf");
        assertThat(c0.getVariable("item.contentType")).isEqualTo("application/pdf");
        assertThat(c0.getVariable("item.index")).isEqualTo(0);
        assertThat(c0.getVariable("item.count")).isEqualTo(2);
        assertThat(c0.getVariable("email.subject")).isEqualTo("Hi"); // unchanged passthrough

        ExecutionContext c1 = result.fanOutContexts().get(1);
        assertThat(c1.getVariable("item.name")).isEqualTo("b.png");
        assertThat(c1.getVariable("item.index")).isEqualTo(1);
    }

    @Test
    void customAlias_isUsedForBinding() {
        ExecutionContext ctx = ctxWith(Map.of("email.attachments", List.of(attachment("a.pdf", "application/pdf"))));

        NodeProcessorResult result = processor.process(
                node("{\"sourceVariable\":\"email.attachments\",\"itemAlias\":\"att\"}"), ctx, userId);

        ExecutionContext c0 = result.fanOutContexts().get(0);
        assertThat(c0.getVariable("att.name")).isEqualTo("a.pdf");
        assertThat(c0.getVariable("att.index")).isEqualTo(0);
    }

    @Test
    void scalarList_bindsAliasToTheValue() {
        ExecutionContext ctx = ctxWith(Map.of("tags", List.of("x", "y")));

        NodeProcessorResult result = processor.process(
                node("{\"sourceVariable\":\"tags\"}"), ctx, userId);

        assertThat(result.fanOutContexts()).hasSize(2);
        assertThat(result.fanOutContexts().get(0).getVariable("item")).isEqualTo("x");
        assertThat(result.fanOutContexts().get(1).getVariable("item")).isEqualTo("y");
    }

    @Test
    void iterationsAreCappedAtMaxIterations() {
        List<Object> big = new ArrayList<>();
        for (int i = 0; i < ForEachNodeProcessor.MAX_ITERATIONS + 1; i++) {
            big.add("v" + i);
        }
        ExecutionContext ctx = ctxWith(Map.of("big", big));

        NodeProcessorResult result = processor.process(node("{\"sourceVariable\":\"big\"}"), ctx, userId);

        assertThat(result.fanOutContexts()).hasSize(ForEachNodeProcessor.MAX_ITERATIONS);
        assertThat(result.detail().get("count")).isEqualTo(ForEachNodeProcessor.MAX_ITERATIONS);
        assertThat(result.detail().get("truncatedFrom")).isEqualTo(ForEachNodeProcessor.MAX_ITERATIONS + 1);
    }
}
