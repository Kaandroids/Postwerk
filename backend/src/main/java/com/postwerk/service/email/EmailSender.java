package com.postwerk.service.email;

/**
 * Abstraction for sending system/transactional email (account verification, password reset,
 * and — later — notification email per the notification-system design).
 *
 * <p>This is the single seam every system-mail feature goes through. The active implementation is
 * chosen by configuration: {@code SmtpEmailSender} (Mailpit in dev, Resend in prod) when
 * {@code app.mail.enabled=true}, otherwise {@code LoggingEmailSender} so tests and no-mail dev
 * environments still start and surface the email (incl. links) in the log.</p>
 *
 * <p>Distinct from the product's per-mailbox sending ({@code MailSendingSupport} /
 * {@code MailConnectionFactory}) which sends from the <em>user's</em> connected account.</p>
 */
public interface EmailSender {

    /**
     * Sends one message. Implementations may throw {@link EmailSendException} on transport failure;
     * callers in account flows generally log and continue (resend is available) rather than failing
     * the surrounding operation.
     */
    void send(EmailMessage message);
}
