package com.postwerk.service.executor;

/**
 * A file to attach to an outgoing SMTP message built by {@link MailSendingSupport}.
 *
 * @param filename    the attachment file name shown to the recipient
 * @param contentType the MIME content type (e.g. {@code application/pdf})
 * @param data        the raw attachment bytes
 *
 * @since 1.0
 */
public record OutgoingAttachment(String filename, String contentType, byte[] data) {}
