package com.postwerk.service.executor;

import java.util.List;

/**
 * Value object describing an outgoing SMTP message to be sent via {@link MailSendingSupport}.
 *
 * <p>{@code html} selects the MIME content type: {@code true} sends an HTML body
 * ({@code text/html; charset=UTF-8}), {@code false} sends a UTF-8 plain-text body.
 * {@code attachments} are added as separate MIME parts; when empty the message stays single-part.</p>
 *
 * @since 1.0
 */
public record OutgoingMail(String to, String cc, String bcc, String subject, String body, boolean html,
                           List<OutgoingAttachment> attachments) {

    /** Null-safe: a {@code null} attachments list is normalised to an empty (immutable) list. */
    public OutgoingMail {
        attachments = attachments == null ? List.of() : attachments;
    }

    /** HTML message with optional CC/BCC recipient lists. */
    public static OutgoingMail html(String to, String cc, String bcc, String subject, String body) {
        return new OutgoingMail(to, cc, bcc, subject, body, true, List.of());
    }

    /** HTML message to a single recipient (no CC/BCC). */
    public static OutgoingMail html(String to, String subject, String body) {
        return new OutgoingMail(to, null, null, subject, body, true, List.of());
    }

    /** Plain-text message to a single recipient (no CC/BCC). */
    public static OutgoingMail plainText(String to, String subject, String body) {
        return new OutgoingMail(to, null, null, subject, body, false, List.of());
    }

    /** Returns a copy of this mail carrying the given attachments. */
    public OutgoingMail withAttachments(List<OutgoingAttachment> attachments) {
        return new OutgoingMail(to, cc, bcc, subject, body, html, attachments);
    }
}
