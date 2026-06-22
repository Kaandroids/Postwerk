package com.postwerk.service;

import com.postwerk.model.AiConversation;
import com.postwerk.model.ConversationPhase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Encapsulates conversation phase-transition logic driven by user messages.
 *
 * <p>Extracted from the assistant service so the OPEN → PLANNING → BUILDING state machine lives in
 * a single, focused place. Behaviour is unchanged from the original inline helpers.</p>
 *
 * @since 1.0
 */
@Component
public class ConversationPhaseManager {

    private static final Logger log = LoggerFactory.getLogger(ConversationPhaseManager.class);
    private static final int PLANNING_STALE_THRESHOLD = 5;

    private static final Pattern CONFIRMATION_PATTERN = Pattern.compile(
            "^(ja|yes|ok|passt|mach|do it|build it|genau|los|perfekt|gut so|sieht gut aus|go ahead|lgtm|jap|klar|yep|yup|sure|bitte|mach das|let's go)[.!,\\s]*$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CANCELLATION_PATTERN = Pattern.compile(
            "^(nein|no|abbrechen|cancel|nevermind|never mind|lass mal|doch nicht|stop|halt|nicht|nö|nope)[.!,\\s]*$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SKIP_PLANNING_PATTERN = Pattern.compile(
            ".*(just do it|build it|mach einfach|einfach machen|don't ask|frag nicht|erstell es|create it|bau es|direkt bauen|ohne fragen|ohne nachfragen).*",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Handles phase transitions triggered by user messages (confirmation/cancellation/staleness/skip).
     */
    public void handleUserMessage(AiConversation conversation, String message, List<Map<String, Object>> messageHistory) {
        ConversationPhase phase = conversation.getPhase();
        String trimmed = message.trim();

        if (phase == ConversationPhase.PLANNING) {
            // Build-intent phrases (e.g. the "Ja, bau es so." / "Yes, build it." confirm button, or a
            // free-typed "bau es") count as confirmation while planning — they unambiguously mean "go build".
            if (CONFIRMATION_PATTERN.matcher(trimmed).matches() || SKIP_PLANNING_PATTERN.matcher(trimmed).matches()) {
                conversation.setPhase(ConversationPhase.BUILDING);
                log.info("Phase transition: PLANNING → BUILDING (user confirmed, conversation {})", conversation.getId());
            } else if (CANCELLATION_PATTERN.matcher(trimmed).matches()) {
                conversation.setPhase(ConversationPhase.OPEN);
                log.info("Phase transition: PLANNING → OPEN (user cancelled, conversation {})", conversation.getId());
            } else {
                // Check for stale planning (too many messages without confirmation)
                int messagesSincePlanning = countMessagesSinceLastPlanProposal(messageHistory);
                if (messagesSincePlanning >= PLANNING_STALE_THRESHOLD) {
                    conversation.setPhase(ConversationPhase.OPEN);
                    log.info("Phase transition: PLANNING → OPEN (stale, {} messages without confirmation, conversation {})",
                            messagesSincePlanning, conversation.getId());
                }
            }
        } else if (phase == ConversationPhase.BUILDING) {
            if (CANCELLATION_PATTERN.matcher(trimmed).matches()) {
                conversation.setPhase(ConversationPhase.OPEN);
                log.info("Phase transition: BUILDING → OPEN (user cancelled mid-build, conversation {})", conversation.getId());
            }
        } else if (phase == ConversationPhase.OPEN) {
            // Skip planning: if user explicitly says "just do it" with automation context
            if (SKIP_PLANNING_PATTERN.matcher(trimmed).matches()) {
                conversation.setPhase(ConversationPhase.BUILDING);
                log.info("Phase transition: OPEN → BUILDING (skip planning, conversation {})", conversation.getId());
            }
        }
    }

    /**
     * Counts user messages since the last assistant message containing a plan proposal tool call.
     */
    private int countMessagesSinceLastPlanProposal(List<Map<String, Object>> messageHistory) {
        int count = 0;
        for (int i = messageHistory.size() - 1; i >= 0; i--) {
            Map<String, Object> msg = messageHistory.get(i);
            String role = (String) msg.get("role");
            if ("user".equals(role)) {
                count++;
            } else if ("assistant".equals(role)) {
                Object toolCalls = msg.get("toolCalls");
                if (toolCalls instanceof List<?> tcList) {
                    for (Object tc : tcList) {
                        if (tc instanceof Map<?, ?> tcMap) {
                            if ("propose_automation_plan".equals(tcMap.get("toolName"))) {
                                return count;
                            }
                        }
                    }
                }
                // If assistant message but no plan tool call, keep counting
            }
        }
        return count;
    }
}
