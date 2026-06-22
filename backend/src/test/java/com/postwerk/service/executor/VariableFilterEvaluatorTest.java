package com.postwerk.service.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VariableFilterEvaluatorTest {

    private VariableFilterEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new VariableFilterEvaluator(new ObjectMapper());
    }

    @Test
    void singleCheck_matches_returnsCheckIndex() {
        String config = """
                {"checks":[{"label":"From check","groups":[{"conditions":[
                    {"field":"email.from","operator":"CONTAINS","value":"sender"}
                ]}]}]}
                """;
        Map<String, Object> variables = Map.of("email.from", "sender@example.com");

        var result = evaluator.evaluate(config, variables);

        assertThat(result.matched()).isTrue();
        assertThat(result.matchedCheckIndex()).isEqualTo(0);
    }

    @Test
    void singleCheck_noMatch_returnsNegativeOne() {
        String config = """
                {"checks":[{"label":"From check","groups":[{"conditions":[
                    {"field":"email.from","operator":"EQUALS","value":"nobody@nowhere.com"}
                ]}]}]}
                """;
        Map<String, Object> variables = Map.of("email.from", "sender@example.com");

        var result = evaluator.evaluate(config, variables);

        assertThat(result.matched()).isFalse();
        assertThat(result.matchedCheckIndex()).isEqualTo(-1);
    }

    @Test
    void multipleChecks_firstMatchWins() {
        String config = """
                {"checks":[
                    {"label":"First","groups":[{"conditions":[{"field":"email.from","operator":"CONTAINS","value":"sender"}]}]},
                    {"label":"Second","groups":[{"conditions":[{"field":"email.from","operator":"CONTAINS","value":"sender"}]}]}
                ]}
                """;
        Map<String, Object> variables = Map.of("email.from", "sender@example.com");

        var result = evaluator.evaluate(config, variables);

        assertThat(result.matched()).isTrue();
        assertThat(result.matchedCheckIndex()).isEqualTo(0);
    }

    @Test
    void multipleChecks_secondMatchWins() {
        String config = """
                {"checks":[
                    {"label":"First","groups":[{"conditions":[{"field":"email.from","operator":"EQUALS","value":"nobody@nowhere.com"}]}]},
                    {"label":"Second","groups":[{"conditions":[{"field":"email.from","operator":"CONTAINS","value":"sender"}]}]}
                ]}
                """;
        Map<String, Object> variables = Map.of("email.from", "sender@example.com");

        var result = evaluator.evaluate(config, variables);

        assertThat(result.matched()).isTrue();
        assertThat(result.matchedCheckIndex()).isEqualTo(1);
    }

    @Test
    void allChecksFail_returnsNegativeOne() {
        String config = """
                {"checks":[
                    {"label":"First","groups":[{"conditions":[{"field":"email.from","operator":"EQUALS","value":"a@a.com"}]}]},
                    {"label":"Second","groups":[{"conditions":[{"field":"email.from","operator":"EQUALS","value":"b@b.com"}]}]}
                ]}
                """;
        Map<String, Object> variables = Map.of("email.from", "sender@example.com");

        var result = evaluator.evaluate(config, variables);

        assertThat(result.matched()).isFalse();
        assertThat(result.matchedCheckIndex()).isEqualTo(-1);
    }

    @Test
    void dnfLogic_groupsAreOr() {
        String config = """
                {"checks":[{"label":"OR test","groups":[
                    {"conditions":[{"field":"email.from","operator":"EQUALS","value":"nobody@nowhere.com"}]},
                    {"conditions":[{"field":"email.from","operator":"CONTAINS","value":"sender"}]}
                ]}]}
                """;
        Map<String, Object> variables = Map.of("email.from", "sender@example.com");

        var result = evaluator.evaluate(config, variables);

        assertThat(result.matched()).isTrue();
        assertThat(result.matchedCheckIndex()).isEqualTo(0);
    }

    @Test
    void dnfLogic_conditionsAreAnd() {
        String config = """
                {"checks":[{"label":"AND test","groups":[{"conditions":[
                    {"field":"email.from","operator":"CONTAINS","value":"sender"},
                    {"field":"email.subject","operator":"CONTAINS","value":"NONEXISTENT"}
                ]}]}]}
                """;
        Map<String, Object> variables = Map.of(
                "email.from", "sender@example.com",
                "email.subject", "Test Email Subject"
        );

        var result = evaluator.evaluate(config, variables);

        assertThat(result.matched()).isFalse();
        assertThat(result.matchedCheckIndex()).isEqualTo(-1);
    }

    @Test
    void emptyChecks_returnsNegativeOne() {
        String config = """
                {"checks":[]}
                """;
        Map<String, Object> variables = Map.of("email.from", "sender@example.com");

        var result = evaluator.evaluate(config, variables);

        assertThat(result.matched()).isFalse();
        assertThat(result.matchedCheckIndex()).isEqualTo(-1);
    }

    @Test
    void numericOperators_greaterThan() {
        String config = """
                {"checks":[{"label":"GT","groups":[{"conditions":[
                    {"field":"extraction_0.amount","operator":"GREATER_THAN","value":"100"}
                ]}]}]}
                """;
        Map<String, Object> variables = Map.of("extraction_0.amount", "150.50");

        var result = evaluator.evaluate(config, variables);

        assertThat(result.matched()).isTrue();
        assertThat(result.matchedCheckIndex()).isEqualTo(0);
    }

    @Test
    void numericOperators_lessThan() {
        String config = """
                {"checks":[{"label":"LT","groups":[{"conditions":[
                    {"field":"extraction_0.count","operator":"LESS_THAN","value":"10"}
                ]}]}]}
                """;
        Map<String, Object> variables = Map.of("extraction_0.count", "5");

        var result = evaluator.evaluate(config, variables);

        assertThat(result.matched()).isTrue();
        assertThat(result.matchedCheckIndex()).isEqualTo(0);
    }

    @Test
    void containsOperator_caseInsensitive() {
        String config = """
                {"checks":[{"label":"Case test","groups":[{"conditions":[
                    {"field":"email.from","operator":"CONTAINS","value":"SENDER"}
                ]}]}]}
                """;
        Map<String, Object> variables = Map.of("email.from", "sender@example.com");

        var result = evaluator.evaluate(config, variables);

        assertThat(result.matched()).isTrue();
        assertThat(result.matchedCheckIndex()).isEqualTo(0);
    }
}
