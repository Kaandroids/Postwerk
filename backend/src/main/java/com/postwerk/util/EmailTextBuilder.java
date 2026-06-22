package com.postwerk.util;

import com.postwerk.model.Email;

/**
 * Utility for building plain-text representations of email messages.
 *
 * <p>Used by AI-powered nodes (categorization, extraction) that need a text-only version
 * of an email for embedding generation or LLM prompts. Falls back from {@code bodyText}
 * to HTML-to-text conversion when plain text is unavailable.</p>
 *
 * @since 1.0
 */
public final class EmailTextBuilder {

    private EmailTextBuilder() {
        // Utility class — no instantiation
    }

    /**
     * Builds a plain-text representation of the email including subject, sender, and body.
     *
     * @param email the email entity to convert
     * @return formatted text suitable for AI processing
     */
    public static String build(Email email) {
        StringBuilder sb = new StringBuilder();
        if (email.getSubject() != null) sb.append("Subject: ").append(email.getSubject()).append("\n");
        if (email.getFromAddress() != null) sb.append("From: ").append(email.getFromAddress()).append("\n");

        String body = email.getBodyText();
        if ((body == null || body.isBlank()) && email.getBodyHtml() != null) {
            body = HtmlToTextUtil.convert(email.getBodyHtml());
        }
        if (body != null) sb.append("\n").append(body);

        return sb.toString();
    }
}
