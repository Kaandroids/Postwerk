package com.postwerk.service;

import com.postwerk.model.AiConversation;
import com.postwerk.model.ConversationPhase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ConversationPhaseManager}, the OPEN → PLANNING → BUILDING state machine.
 *
 * <p>Focuses on the regression where rejecting an automation plan via the FE cancel button left the
 * conversation stuck in PLANNING: the button copy ("Nein, abbrechen." / "No, cancel.") did not match
 * the anchored cancellation pattern, so no transition fired.</p>
 */
class ConversationPhaseManagerTest {

    private final ConversationPhaseManager manager = new ConversationPhaseManager();

    private static AiConversation conversation(ConversationPhase phase) {
        return AiConversation.builder().phase(phase).build();
    }

    // --- The bug: cancel-button copy must move PLANNING → OPEN ----------------------------------

    @Test
    void planning_germanCancelButtonCopy_returnsToOpen() {
        AiConversation conv = conversation(ConversationPhase.PLANNING);

        manager.handleUserMessage(conv, "Nein, abbrechen.", List.of());

        assertThat(conv.getPhase()).isEqualTo(ConversationPhase.OPEN);
    }

    @Test
    void planning_englishCancelButtonCopy_returnsToOpen() {
        AiConversation conv = conversation(ConversationPhase.PLANNING);

        manager.handleUserMessage(conv, "No, cancel.", List.of());

        assertThat(conv.getPhase()).isEqualTo(ConversationPhase.OPEN);
    }

    @Test
    void planning_bareCancelWord_returnsToOpen() {
        AiConversation conv = conversation(ConversationPhase.PLANNING);

        manager.handleUserMessage(conv, "Nein.", List.of());

        assertThat(conv.getPhase()).isEqualTo(ConversationPhase.OPEN);
    }

    @Test
    void building_cancelButtonCopy_returnsToOpen() {
        AiConversation conv = conversation(ConversationPhase.BUILDING);

        manager.handleUserMessage(conv, "Nein, abbrechen.", List.of());

        assertThat(conv.getPhase()).isEqualTo(ConversationPhase.OPEN);
    }

    // --- No false positives: "cancel"/"abbrechen" mid-sentence is a real planning message --------

    @Test
    void planning_cancelKeywordMidSentence_staysInPlanning() {
        AiConversation conv = conversation(ConversationPhase.PLANNING);

        manager.handleUserMessage(conv, "Baue mir eine Automation die eine Bestellung abbrechen kann", List.of());

        assertThat(conv.getPhase()).isEqualTo(ConversationPhase.PLANNING);
    }

    // --- Confirmation still works (guard against regressing the earlier fix) ---------------------

    @Test
    void planning_confirmButtonCopy_movesToBuilding() {
        AiConversation conv = conversation(ConversationPhase.PLANNING);

        manager.handleUserMessage(conv, "Ja, bau es so.", List.of());

        assertThat(conv.getPhase()).isEqualTo(ConversationPhase.BUILDING);
    }

    @Test
    void open_skipPlanning_movesToBuilding() {
        AiConversation conv = conversation(ConversationPhase.OPEN);

        manager.handleUserMessage(conv, "Mach einfach, ohne nachfragen", List.of());

        assertThat(conv.getPhase()).isEqualTo(ConversationPhase.BUILDING);
    }

    // --- Staleness fallback: many turns without confirmation drops back to OPEN ------------------

    @Test
    void planning_staleAfterManyMessages_returnsToOpen() {
        AiConversation conv = conversation(ConversationPhase.PLANNING);
        List<Map<String, Object>> history = List.of(
                Map.of("role", "user", "content", "a"),
                Map.of("role", "user", "content", "b"),
                Map.of("role", "user", "content", "c"),
                Map.of("role", "user", "content", "d"),
                Map.of("role", "user", "content", "e")
        );

        manager.handleUserMessage(conv, "hmm, was meinst du genau?", history);

        assertThat(conv.getPhase()).isEqualTo(ConversationPhase.OPEN);
    }
}
