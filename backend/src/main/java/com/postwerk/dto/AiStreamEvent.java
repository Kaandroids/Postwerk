package com.postwerk.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Server-Sent Event payload for streaming AI chat responses.
 * Each event represents a step in the AI tool-calling loop.
 *
 * @param type           Event type: tool_start, tool_result, reply, error, done, cancelled, phase
 * @param tool           Tool name (for tool_start/tool_result events)
 * @param args           Tool arguments (for tool_start events)
 * @param result         Tool execution result (for tool_result events)
 * @param success        Whether the tool call succeeded (for tool_result events)
 * @param content        Text content (for reply/error events)
 * @param conversationId The conversation UUID
 * @param phase          Current conversation phase (for phase/done events)
 * @param validationIssues Validation issues from the validator (for tool_result events, optional)
 */
public record AiStreamEvent(
        String type,
        String tool,
        Map<String, Object> args,
        Object result,
        Boolean success,
        String content,
        UUID conversationId,
        String phase,
        Object validationIssues
) {
    public static AiStreamEvent toolStart(String tool, Map<String, Object> args, UUID conversationId) {
        return new AiStreamEvent("tool_start", tool, args, null, null, null, conversationId, null, null);
    }

    public static AiStreamEvent toolResult(String tool, Object result, boolean success, UUID conversationId) {
        return new AiStreamEvent("tool_result", tool, null, result, success, null, conversationId, null, null);
    }

    public static AiStreamEvent toolResult(String tool, Object result, boolean success, UUID conversationId, Object validationIssues) {
        return new AiStreamEvent("tool_result", tool, null, result, success, null, conversationId, null, validationIssues);
    }

    public static AiStreamEvent reply(String content, UUID conversationId) {
        return new AiStreamEvent("reply", null, null, null, null, content, conversationId, null, null);
    }

    public static AiStreamEvent error(String message, UUID conversationId) {
        return new AiStreamEvent("error", null, null, null, null, message, conversationId, null, null);
    }

    public static AiStreamEvent done(UUID conversationId, String phase) {
        return new AiStreamEvent("done", null, null, null, null, null, conversationId, phase, null);
    }

    public static AiStreamEvent cancelled(UUID conversationId) {
        return new AiStreamEvent("cancelled", null, null, null, null, null, conversationId, null, null);
    }

    public static AiStreamEvent phase(String phase, UUID conversationId) {
        return new AiStreamEvent("phase", null, null, null, null, null, conversationId, phase, null);
    }
}
