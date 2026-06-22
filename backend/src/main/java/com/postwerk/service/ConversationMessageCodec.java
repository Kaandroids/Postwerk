package com.postwerk.service;

import com.postwerk.dto.AiMessageDto;
import com.postwerk.dto.AiToolCallDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates (de)serialization and Gemini-content reconstruction for AI conversation messages.
 *
 * <p>Extracted from the assistant service to isolate the JSON/Gemini message-shape concerns from
 * conversation orchestration. Behaviour is unchanged from the original inline helpers.</p>
 *
 * @since 1.0
 */
@Component
public class ConversationMessageCodec {

    private static final Logger log = LoggerFactory.getLogger(ConversationMessageCodec.class);
    private static final int MAX_CONVERSATION_MESSAGES = 40;

    private final ObjectMapper objectMapper;

    public ConversationMessageCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> deserialize(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize conversation messages", e);
            return new ArrayList<>();
        }
    }

    public String serialize(List<Map<String, Object>> messages) {
        try {
            return objectMapper.writeValueAsString(messages);
        } catch (Exception e) {
            log.warn("Failed to serialize conversation messages", e);
            return "[]";
        }
    }

    /** Keeps the first message (for context) plus the last {@code MAX_CONVERSATION_MESSAGES - 1}. */
    public List<Map<String, Object>> trim(List<Map<String, Object>> messages) {
        if (messages.size() <= MAX_CONVERSATION_MESSAGES) {
            return messages;
        }
        List<Map<String, Object>> trimmed = new ArrayList<>();
        trimmed.add(messages.get(0));
        int start = messages.size() - (MAX_CONVERSATION_MESSAGES - 1);
        trimmed.addAll(messages.subList(start, messages.size()));
        return trimmed;
    }

    @SuppressWarnings("unchecked")
    public List<Content> buildContents(List<Map<String, Object>> messageHistory) {
        List<Content> contents = new ArrayList<>();

        for (Map<String, Object> msg : messageHistory) {
            String role = (String) msg.get("role");
            String content = (String) msg.get("content");

            if ("user".equals(role)) {
                contents.add(Content.builder()
                        .role("user")
                        .parts(List.of(Part.fromText(content != null ? content : "")))
                        .build());
            } else if ("assistant".equals(role)) {
                List<Part> parts = new ArrayList<>();
                if (content != null && !content.isBlank()) {
                    parts.add(Part.fromText(content));
                }

                // Reconstruct tool calls so Gemini retains full conversation context
                Object tcRaw = msg.get("toolCalls");
                if (tcRaw instanceof List<?> tcList) {
                    for (Object tc : tcList) {
                        if (tc instanceof Map<?, ?> tcMap) {
                            String toolName = (String) tcMap.get("toolName");
                            if (toolName == null) toolName = (String) tcMap.get("tool");
                            Object argsObj = tcMap.get("args");
                            if (toolName != null && argsObj instanceof Map<?, ?> argsMap) {
                                parts.add(Part.fromFunctionCall(toolName, (Map<String, Object>) argsMap));
                            }
                        }
                    }
                }

                if (parts.isEmpty()) {
                    parts.add(Part.fromText(""));
                }

                contents.add(Content.builder()
                        .role("model")
                        .parts(parts)
                        .build());

                // Add function responses for tool calls (Gemini expects them as user role)
                if (tcRaw instanceof List<?> tcList2) {
                    List<Part> responseParts = new ArrayList<>();
                    for (Object tc : tcList2) {
                        if (tc instanceof Map<?, ?> tcMap) {
                            String toolName = (String) tcMap.get("toolName");
                            if (toolName == null) toolName = (String) tcMap.get("tool");
                            Object data = tcMap.get("data");
                            boolean success = Boolean.TRUE.equals(tcMap.get("success"));
                            if (toolName != null) {
                                Map<String, Object> result = new LinkedHashMap<>();
                                result.put("success", success);
                                if (data != null) result.put("data", data);
                                responseParts.add(Part.fromFunctionResponse(toolName, result));
                            }
                        }
                    }
                    if (!responseParts.isEmpty()) {
                        contents.add(Content.builder()
                                .role("user")
                                .parts(responseParts)
                                .build());
                    }
                }
            }
        }

        return contents;
    }

    public AiMessageDto toAiMessageDto(Map<String, Object> raw) {
        String role = (String) raw.get("role");
        String content = (String) raw.get("content");
        String timestamp = (String) raw.get("timestamp");
        Instant ts = timestamp != null ? Instant.parse(timestamp) : null;

        List<AiToolCallDto> toolCalls = null;
        Object tcRaw = raw.get("toolCalls");
        if (tcRaw instanceof List<?> tcList) {
            toolCalls = objectMapper.convertValue(tcList, new TypeReference<>() {});
        }

        return new AiMessageDto(role, content, ts, toolCalls);
    }
}
