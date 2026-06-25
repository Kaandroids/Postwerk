package com.postwerk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.postwerk.dto.AiMessageDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ConversationMessageCodec}. Uses a real {@link ObjectMapper}; covers the
 * JSON (de)serialization, the conversation-window trim, the {@link AiMessageDto} mapping and the
 * Gemini {@link Content} reconstruction (text + function-call/function-response replay).
 */
class ConversationMessageCodecTest {

    private final ConversationMessageCodec codec = new ConversationMessageCodec(new ObjectMapper());

    // ── deserialize / serialize ──────────────────────────────────────────

    @Test
    void deserializesValidJsonIntoMessageMaps() {
        var messages = codec.deserialize("[{\"role\":\"user\",\"content\":\"hi\"}]");

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).containsEntry("role", "user").containsEntry("content", "hi");
    }

    @Test
    void deserializeReturnsEmptyListOnMalformedJson() {
        assertThat(codec.deserialize("not-json")).isEmpty();
    }

    @Test
    void serializeRoundTrips() {
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "user", "content", "hello"),
                Map.of("role", "assistant", "content", "hi there"));

        String json = codec.serialize(messages);

        assertThat(codec.deserialize(json)).isEqualTo(messages);
    }

    // ── trim (keep first + last MAX-1) ───────────────────────────────────

    @Test
    void trimReturnsSameListWhenWithinLimit() {
        List<Map<String, Object>> messages = buildIndexedMessages(40);
        assertThat(codec.trim(messages)).isSameAs(messages);
    }

    @Test
    void trimKeepsFirstMessageAndLastWindowWhenOverLimit() {
        List<Map<String, Object>> messages = buildIndexedMessages(45); // 5 over the cap of 40

        List<Map<String, Object>> trimmed = codec.trim(messages);

        assertThat(trimmed).hasSize(40);
        assertThat(trimmed.get(0)).containsEntry("content", "0");   // first message preserved
        assertThat(trimmed.get(1)).containsEntry("content", "6");   // 45 - 39 = index 6 starts the tail
        assertThat(trimmed.get(39)).containsEntry("content", "44"); // last message preserved
    }

    // ── toAiMessageDto ───────────────────────────────────────────────────

    @Test
    void mapsFullMessageToDto() {
        Map<String, Object> raw = Map.of(
                "role", "assistant",
                "content", "done",
                "timestamp", "2026-01-02T03:04:05Z",
                "toolCalls", List.of(Map.of(
                        "tool", "create_automation",
                        "args", Map.of("name", "x"),
                        "result", Map.of("id", 1),
                        "success", true)));

        AiMessageDto dto = codec.toAiMessageDto(raw);

        assertThat(dto.role()).isEqualTo("assistant");
        assertThat(dto.content()).isEqualTo("done");
        assertThat(dto.timestamp()).isEqualTo(Instant.parse("2026-01-02T03:04:05Z"));
        assertThat(dto.toolCalls()).hasSize(1);
        assertThat(dto.toolCalls().get(0).tool()).isEqualTo("create_automation");
        assertThat(dto.toolCalls().get(0).success()).isTrue();
    }

    @Test
    void mapsMessageWithoutTimestampOrToolCalls() {
        AiMessageDto dto = codec.toAiMessageDto(Map.of("role", "user", "content", "hi"));

        assertThat(dto.timestamp()).isNull();
        assertThat(dto.toolCalls()).isNull();
    }

    // ── buildContents (Gemini reconstruction) ────────────────────────────

    @Test
    void buildContentsMapsUserMessageToUserTextContent() {
        List<Content> contents = codec.buildContents(List.of(
                Map.of("role", "user", "content", "what can you do?")));

        assertThat(contents).hasSize(1);
        assertThat(contents.get(0).role()).contains("user");
        assertThat(textOf(contents.get(0))).containsExactly("what can you do?");
    }

    @Test
    void buildContentsMapsAssistantTextToModelContent() {
        List<Content> contents = codec.buildContents(List.of(
                Map.of("role", "assistant", "content", "I can build automations.")));

        assertThat(contents).hasSize(1);
        assertThat(contents.get(0).role()).contains("model");
        assertThat(textOf(contents.get(0))).containsExactly("I can build automations.");
    }

    @Test
    void buildContentsReplaysToolCallAsModelCallThenUserResponse() {
        Map<String, Object> assistant = Map.of(
                "role", "assistant",
                "content", "",
                "toolCalls", List.of(Map.of(
                        "toolName", "create_automation",
                        "args", Map.of("name", "Invoices"),
                        "data", Map.of("id", "auto-1"),
                        "success", true)));

        List<Content> contents = codec.buildContents(List.of(assistant));

        // a "model" content carrying the function call, followed by a "user" content with the response
        assertThat(contents).hasSize(2);
        assertThat(contents.get(0).role()).contains("model");
        assertThat(contents.get(0).parts().orElseThrow().get(0).functionCall()).isPresent();
        assertThat(contents.get(1).role()).contains("user");
        assertThat(contents.get(1).parts().orElseThrow().get(0).functionResponse()).isPresent();
    }

    @Test
    void buildContentsEmitsEmptyTextPartForBlankAssistantMessage() {
        List<Content> contents = codec.buildContents(List.of(
                Map.of("role", "assistant", "content", "")));

        assertThat(contents).hasSize(1);
        assertThat(contents.get(0).role()).contains("model");
        assertThat(textOf(contents.get(0))).containsExactly("");
    }

    @Test
    void buildContentsIgnoresUnknownRoles() {
        List<Content> contents = codec.buildContents(List.of(
                Map.of("role", "system", "content", "ignored")));

        assertThat(contents).isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static List<Map<String, Object>> buildIndexedMessages(int n) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            messages.add(Map.of("role", "user", "content", String.valueOf(i)));
        }
        return messages;
    }

    /** Text of every text-bearing part of a content, in order. */
    private static List<String> textOf(Content content) {
        return content.parts().orElseThrow().stream()
                .map(p -> p.text().orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
