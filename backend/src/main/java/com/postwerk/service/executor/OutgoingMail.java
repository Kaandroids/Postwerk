package com.postwerk.service.executor;

/**
 * Value object describing an outgoing SMTP message to be sent via {@link MailSendingSupport}.
 *
 * <p>{@code html} selects the MIME content type: {@code true} sends an HTML body
 * ({@code text/html; charset=UTF-8}), {@code false} sends a UTF-8 plain-text body.</p>
 *
 * @since 1.0
 */
public record OutgoingMail(String to, String cc, String bcc, String subject, String body, boolean html) {

    /** HTML message with optional CC/BCC recipient lists. */
    public static OutgoingMail html(String to, String cc, String bcc, String subject, String body) {
        return new OutgoingMail(to, cc, bcc, subject, body, true);
    }

    /** HTML message to a single recipient (no CC/BCC). */
    public static OutgoingMail html(String to, String subject, String body) {
        return new OutgoingMail(to, null, null, subject, body, true);
    }

    /** Plain-text message to a single recipient (no CC/BCC). */
    public static OutgoingMail plainText(String to, String subject, String body) {
        return new OutgoingMail(to, null, null, subject, body, false);
    }
}
