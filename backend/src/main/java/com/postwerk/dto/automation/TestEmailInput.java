package com.postwerk.dto.automation;

import java.util.List;
import java.util.Map;

/**
 * DTO defining the synthetic input for an automation test case.
 *
 * <p>For TRIGGER-started (EMAIL) automations the email fields ({@code from/to/subject/body})
 * describe the incoming message. Fields are optional so the same DTO can describe non-email
 * starts:</p>
 * <ul>
 *   <li><b>{@code triggerPayload}</b> — seeds {@code trigger.*} variables for WEBHOOK-trigger
 *       automations (the inbound payload the user expects to receive).</li>
 *   <li><b>{@code inputFields}</b> — seeds {@code input.*} variables for INTEGRATION-kind
 *       automations started from their INPUT node.</li>
 * </ul>
 */
public record TestEmailInput(
        String from,
        String to,
        String subject,
        String body,
        String receivedAt,
        String inReplyTo,
        List<String> categoryIds,
        Map<String, Object> triggerPayload,
        Map<String, Object> inputFields
) {}
