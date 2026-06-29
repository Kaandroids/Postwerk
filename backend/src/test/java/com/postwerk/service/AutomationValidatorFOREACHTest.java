package com.postwerk.service;

import com.postwerk.model.enums.AutomationKind;
import com.postwerk.service.AutomationValidator.EdgeView;
import com.postwerk.service.AutomationValidator.NodeView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the FOREACH rules added to {@link AutomationValidator}: the no-source error and the alias
 * namespace exposure that keeps downstream {@code item.*} references from being flagged dangling.
 */
class AutomationValidatorFOREACHTest {

    private AutomationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AutomationValidator(new ObjectMapper());
    }

    @Test
    void foreach_withoutSource_flagsError() {
        List<NodeView> nodes = List.of(
                new NodeView("t1", "TRIGGER", "Trigger", "{\"triggerMode\":\"EMAIL\"}", null),
                new NodeView("f1", "FOREACH", "Loop", "{}", null));
        List<EdgeView> edges = List.of(new EdgeView("t1", "new-email", "f1", null));

        var result = validator.validate(AutomationKind.AUTOMATION, nodes, edges, Set.of());

        assertThat(result.issues())
                .anyMatch(i -> "FOREACH_NO_SOURCE".equals(i.code()) && "f1".equals(i.nodeId()));
        assertThat(result.valid()).isFalse();
    }

    @Test
    void foreach_exposesItemNamespace_soDownstreamItemRefsAreNotDangling() {
        List<NodeView> nodes = List.of(
                new NodeView("t1", "TRIGGER", "Trigger", "{\"triggerMode\":\"EMAIL\"}", null),
                new NodeView("f1", "FOREACH", "Loop",
                        "{\"sourceVariable\":\"email.attachments\",\"itemAlias\":\"item\"}", null),
                new NodeView("s1", "SEND_EMAIL", "Send",
                        "{\"to\":\"a@b.com\",\"subject\":\"{{item.name}}\",\"body\":\"x\"}", null));
        List<EdgeView> edges = List.of(
                new EdgeView("t1", "new-email", "f1", null),
                new EdgeView("f1", "each", "s1", null));

        var result = validator.validate(AutomationKind.AUTOMATION, nodes, edges, Set.of());

        assertThat(result.issues()).noneMatch(i -> "FOREACH_NO_SOURCE".equals(i.code()));
        assertThat(result.issues())
                .noneMatch(i -> "DANGLING_VARIABLE".equals(i.code()) && "s1".equals(i.nodeId()));
    }
}
