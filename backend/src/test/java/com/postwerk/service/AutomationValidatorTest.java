package com.postwerk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postwerk.dto.automation.AutomationValidationResult;
import com.postwerk.dto.automation.ValidationIssue;
import com.postwerk.model.enums.AutomationKind;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the shared semantic linter {@link AutomationValidator}. Fully offline and
 * deterministic — the validator's only dependency is a Jackson {@link ObjectMapper}, so a real
 * instance is used. The suite locks the shared rule-catalog vocabulary (issue codes + severities)
 * that gates activate/publish and that the frontend {@code AutomationLintService} mirrors.
 */
class AutomationValidatorTest {

    private final AutomationValidator validator = new AutomationValidator(new ObjectMapper());

    // ── helpers ──────────────────────────────────────────────────────────

    private static AutomationValidator.NodeView node(String id, String type, String config) {
        return new AutomationValidator.NodeView(id, type, null, config, null);
    }

    private static AutomationValidator.EdgeView edge(String from, String to) {
        return new AutomationValidator.EdgeView(from, null, to, null);
    }

    private AutomationValidationResult validateAutomation(List<AutomationValidator.NodeView> nodes,
                                                          List<AutomationValidator.EdgeView> edges) {
        return validator.validate(AutomationKind.AUTOMATION, nodes, edges, Set.of());
    }

    private static List<String> codes(AutomationValidationResult r) {
        return r.issues().stream().map(ValidationIssue::code).toList();
    }

    private static String severityOf(AutomationValidationResult r, String code) {
        return r.issues().stream()
                .filter(i -> i.code().equals(code))
                .map(ValidationIssue::severity)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no issue with code " + code));
    }

    // ── MISSING_TRIGGER ──────────────────────────────────────────────────

    @Nested
    class MissingTrigger {
        @Test
        void automationWithoutTrigger_isError() {
            var result = validateAutomation(
                    List.of(node("c", "CATEGORIZE", "{\"categoryIds\":[\"x\"]}")),
                    List.of());
            assertThat(codes(result)).contains("MISSING_TRIGGER");
            assertThat(severityOf(result, "MISSING_TRIGGER")).isEqualTo("error");
            assertThat(result.valid()).isFalse();
        }

        @Test
        void automationWithTrigger_noMissingTrigger() {
            var result = validateAutomation(
                    List.of(node("t", "TRIGGER", "{\"triggerMode\":\"EMAIL\"}")),
                    List.of());
            assertThat(codes(result)).doesNotContain("MISSING_TRIGGER");
        }

        @Test
        void integrationWithoutTrigger_isAllowed() {
            var result = validator.validate(AutomationKind.INTEGRATION,
                    List.of(node("in", "INPUT", "{\"parameterSetId\":\"ps\"}")),
                    List.of(), Set.of());
            assertThat(codes(result)).doesNotContain("MISSING_TRIGGER");
        }
    }

    // ── ORPHAN_NODE ──────────────────────────────────────────────────────

    @Nested
    class OrphanNode {
        @Test
        void nodeWithoutIncomingEdge_isWarning() {
            var result = validateAutomation(
                    List.of(node("t", "TRIGGER", "{}"),
                            node("c", "CATEGORIZE", "{\"categoryIds\":[\"x\"]}")),
                    List.of()); // no edge into c
            assertThat(codes(result)).contains("ORPHAN_NODE");
            assertThat(severityOf(result, "ORPHAN_NODE")).isEqualTo("warning");
        }

        @Test
        void triggerAndInputAreNeverOrphans() {
            var result = validator.validate(AutomationKind.INTEGRATION,
                    List.of(node("t", "TRIGGER", "{}"),
                            node("in", "INPUT", "{\"parameterSetId\":\"ps\"}")),
                    List.of(), Set.of());
            assertThat(codes(result)).doesNotContain("ORPHAN_NODE");
        }

        @Test
        void connectedNode_isNotOrphan() {
            var result = validateAutomation(
                    List.of(node("t", "TRIGGER", "{}"),
                            node("c", "CATEGORIZE", "{\"categoryIds\":[\"x\"]}")),
                    List.of(edge("t", "c")));
            assertThat(codes(result)).doesNotContain("ORPHAN_NODE");
        }
    }

    // ── per-node required config ─────────────────────────────────────────

    @Nested
    class RequiredConfig {
        @Test
        void extractWithoutParameterSet_isError() {
            assertThat(codes(validateAutomation(
                    List.of(node("t", "TRIGGER", "{}"), node("e", "EXTRACT", "{}")),
                    List.of(edge("t", "e"))))).contains("EXTRACT_NO_PARAMSET");

            // present array but an entry missing its parameterSetId is still an error
            assertThat(codes(validateAutomation(
                    List.of(node("t", "TRIGGER", "{}"),
                            node("e", "EXTRACT", "{\"extractions\":[{}]}")),
                    List.of(edge("t", "e"))))).contains("EXTRACT_NO_PARAMSET");
        }

        @Test
        void extractWithParameterSet_isOk() {
            assertThat(codes(validateAutomation(
                    List.of(node("t", "TRIGGER", "{}"),
                            node("e", "EXTRACT", "{\"extractions\":[{\"parameterSetId\":\"ps\"}]}")),
                    List.of(edge("t", "e"))))).doesNotContain("EXTRACT_NO_PARAMSET");
        }

        @Test
        void categorizeWithoutCategories_isError() {
            assertThat(codes(validateAutomation(
                    List.of(node("t", "TRIGGER", "{}"), node("c", "CATEGORIZE", "{}")),
                    List.of(edge("t", "c"))))).contains("CATEGORIZE_NO_CATEGORIES");
        }

        @Test
        void labelAndRemoveLabelWithoutCategory_isError() {
            assertThat(codes(validateAutomation(
                    List.of(node("t", "TRIGGER", "{}"), node("l", "LABEL", "{}")),
                    List.of(edge("t", "l"))))).contains("LABEL_NO_CATEGORY");
            assertThat(codes(validateAutomation(
                    List.of(node("t", "TRIGGER", "{}"), node("r", "REMOVE_LABEL", "{}")),
                    List.of(edge("t", "r"))))).contains("LABEL_NO_CATEGORY");
        }

        @Test
        void sendEmailWithoutRecipient_isError() {
            assertThat(codes(validateAutomation(
                    List.of(node("t", "TRIGGER", "{}"), node("s", "SEND_EMAIL", "{}")),
                    List.of(edge("t", "s"))))).contains("SEND_NO_RECIPIENT");
        }

        @Test
        void webhookWithoutUrl_isError() {
            assertThat(codes(validateAutomation(
                    List.of(node("t", "TRIGGER", "{}"), node("w", "WEBHOOK", "{}")),
                    List.of(edge("t", "w"))))).contains("WEBHOOK_NO_URL");
        }

        @Test
        void integrationCallWithoutReference_isError() {
            assertThat(codes(validateAutomation(
                    List.of(node("t", "TRIGGER", "{}"), node("i", "INTEGRATION_CALL", "{}")),
                    List.of(edge("t", "i"))))).contains("INTEGRATION_NO_REF");
        }

        @Test
        void inputAndOutputWithoutParameterSet_isError() {
            var result = validator.validate(AutomationKind.INTEGRATION,
                    List.of(node("in", "INPUT", "{}"), node("out", "OUTPUT", "{}")),
                    List.of(edge("in", "out")), Set.of());
            assertThat(codes(result)).contains("INTEGRATION_NO_REF");
            assertThat(result.issues().stream()
                    .filter(i -> i.code().equals("INTEGRATION_NO_REF")).count()).isEqualTo(2);
        }

        @Test
        void vectorSearchMissingKbAndQuery_areTwoErrors() {
            var result = validateAutomation(
                    List.of(node("t", "TRIGGER", "{}"), node("v", "VECTOR_SEARCH", "{}")),
                    List.of(edge("t", "v")));
            assertThat(codes(result)).contains("VECTOR_SEARCH_NO_KB", "VECTOR_SEARCH_NO_QUERY");
        }

        @Test
        void notifyMissingRecipientOnly() {
            var result = validateAutomation(
                    List.of(node("t", "TRIGGER", "{}"),
                            node("n", "NOTIFY", "{\"message\":\"hi\"}")),
                    List.of(edge("t", "n")));
            assertThat(codes(result)).contains("NOTIFY_NO_RECIPIENT").doesNotContain("NOTIFY_NO_MESSAGE");
        }

        @Test
        void notifyMissingMessageOnly() {
            var result = validateAutomation(
                    List.of(node("t", "TRIGGER", "{}"),
                            node("n", "NOTIFY", "{\"recipientUserId\":\"u1\"}")),
                    List.of(edge("t", "n")));
            assertThat(codes(result)).contains("NOTIFY_NO_MESSAGE").doesNotContain("NOTIFY_NO_RECIPIENT");
        }
    }

    // ── EMAIL_ACTION (mode-dependent target) ─────────────────────────────

    @Nested
    class EmailAction {
        private List<String> validate(String config) {
            return codes(validateAutomation(
                    List.of(node("t", "TRIGGER", "{}"), node("a", "EMAIL_ACTION", config)),
                    List.of(edge("t", "a"))));
        }

        @Test
        void forwardWithoutToAddress_isError() {
            assertThat(validate("{\"actionMode\":\"FORWARD\"}")).contains("EMAILACTION_NO_TARGET");
        }

        @Test
        void moveFolderWithoutFolder_isError() {
            assertThat(validate("{\"actionMode\":\"MOVE_FOLDER\"}")).contains("EMAILACTION_NO_TARGET");
        }

        @Test
        void replyManualWithoutBody_isError() {
            assertThat(validate("{\"actionMode\":\"REPLY\",\"contentSource\":\"MANUAL\"}"))
                    .contains("EMAILACTION_NO_TARGET");
        }

        @Test
        void replyTemplateWithoutTemplate_isError() {
            assertThat(validate("{\"actionMode\":\"REPLY\"}")).contains("EMAILACTION_NO_TARGET");
        }

        @Test
        void forwardWithToAddress_isOk() {
            assertThat(validate("{\"actionMode\":\"FORWARD\",\"toAddress\":\"x@y.com\"}"))
                    .doesNotContain("EMAILACTION_NO_TARGET");
        }
    }

    // ── DANGLING_VARIABLE (namespace-level) ──────────────────────────────

    @Nested
    class DanglingVariable {
        @Test
        void referenceToUnavailableNamespace_isWarning() {
            var result = validateAutomation(
                    List.of(node("t", "TRIGGER", "{}"),
                            node("s", "SEND_EMAIL",
                                    "{\"to\":\"x@y.com\",\"body\":\"{{extraction_0.amount}}\"}")),
                    List.of(edge("t", "s")));
            assertThat(codes(result)).contains("DANGLING_VARIABLE");
            assertThat(severityOf(result, "DANGLING_VARIABLE")).isEqualTo("warning");
        }

        @Test
        void referenceProducedByUpstreamNode_isNotDangling() {
            var result = validateAutomation(
                    List.of(node("t", "TRIGGER", "{}"),
                            node("e", "EXTRACT", "{\"extractions\":[{\"parameterSetId\":\"ps\"}]}"),
                            node("s", "SEND_EMAIL",
                                    "{\"to\":\"x@y.com\",\"body\":\"{{extraction_0.amount}}\"}")),
                    List.of(edge("t", "e"), edge("e", "s")));
            assertThat(codes(result)).doesNotContain("DANGLING_VARIABLE");
        }

        @Test
        void constNamespace_isAlwaysAllowed() {
            var result = validateAutomation(
                    List.of(node("t", "TRIGGER", "{}"),
                            node("s", "SEND_EMAIL",
                                    "{\"to\":\"x@y.com\",\"body\":\"{{const.greeting}}\"}")),
                    List.of(edge("t", "s")));
            assertThat(codes(result)).doesNotContain("DANGLING_VARIABLE");
        }

        @Test
        void triggerNamespace_resolvesFromUpstreamTrigger() {
            var result = validateAutomation(
                    List.of(node("t", "TRIGGER", "{\"triggerMode\":\"EMAIL\"}"),
                            node("s", "SEND_EMAIL",
                                    "{\"to\":\"x@y.com\",\"body\":\"{{trigger.subject}} {{email.from}}\"}")),
                    List.of(edge("t", "s")));
            assertThat(codes(result)).doesNotContain("DANGLING_VARIABLE");
        }
    }

    // ── happy path & robustness ──────────────────────────────────────────

    @Nested
    class ValidAndRobust {
        @Test
        void fullyValidAutomation_hasNoIssues() {
            var result = validateAutomation(
                    List.of(node("t", "TRIGGER", "{\"triggerMode\":\"EMAIL\"}"),
                            node("c", "CATEGORIZE", "{\"categoryIds\":[\"cat-1\"]}")),
                    List.of(edge("t", "c")));
            assertThat(result.valid()).isTrue();
            assertThat(result.issues()).isEmpty();
        }

        @Test
        void warningsAloneKeepResultValid() {
            // an orphan CATEGORIZE (configured) produces only a warning → still valid
            var result = validateAutomation(
                    List.of(node("t", "TRIGGER", "{}"),
                            node("c", "CATEGORIZE", "{\"categoryIds\":[\"cat-1\"]}")),
                    List.of()); // c is orphan (warning), trigger present
            assertThat(codes(result)).containsExactly("ORPHAN_NODE");
            assertThat(result.valid()).isTrue();
        }

        @Test
        void malformedConfigJson_doesNotThrowAndFallsBackToEmpty() {
            // invalid JSON is treated as empty config → required-config rule fires, no exception
            var result = validateAutomation(
                    List.of(node("t", "TRIGGER", "{}"),
                            node("w", "WEBHOOK", "not-json")),
                    List.of(edge("t", "w")));
            assertThat(codes(result)).contains("WEBHOOK_NO_URL");
        }

        @Test
        void nullConstantNames_isHandled() {
            var result = validator.validate(AutomationKind.AUTOMATION,
                    List.of(node("t", "TRIGGER", "{}")), List.of(), null);
            assertThat(result.valid()).isTrue();
        }
    }
}
