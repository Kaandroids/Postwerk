package com.postwerk.service.email;

/**
 * A single transactional/system email to be delivered by an {@link EmailSender}.
 *
 * <p>{@code textBody} is optional: when present the message is sent as a multipart
 * text+HTML alternative (better deliverability); when null an HTML-only message is sent.</p>
 */
public record EmailMessage(String to, String subject, String htmlBody, String textBody) {

    /** Convenience constructor for an HTML-only message. */
    public EmailMessage(String to, String subject, String htmlBody) {
        this(to, subject, htmlBody, null);
    }
}
