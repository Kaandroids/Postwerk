package com.postwerk.service;

import com.postwerk.config.EncryptionConfig;
import com.postwerk.model.EmailAccount;
import com.postwerk.util.HostSecurityValidator;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.mail.*;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Centralized factory for creating IMAP {@link Store} and SMTP {@link Session} connections.
 *
 * <p>Consolidates all mail connection configuration in one place, eliminating duplicated
 * property setup across {@code EmailSyncService} and action executors. Connection timeouts
 * are standardized at 15s (connect) and 30s (read).</p>
 *
 * @since 1.0
 */
@Component
public class MailConnectionFactory {

    private static final String TIMEOUT_CONNECT = "15000";
    private static final String TIMEOUT_READ = "30000";

    private final EncryptionConfig encryption;
    private final HostSecurityValidator hostSecurityValidator;

    public MailConnectionFactory(EncryptionConfig encryption, HostSecurityValidator hostSecurityValidator) {
        this.encryption = encryption;
        this.hostSecurityValidator = hostSecurityValidator;
    }

    /**
     * Opens an authenticated IMAP {@link Store} for the given email account.
     *
     * @param account the email account with encrypted IMAP credentials
     * @return an open, connected IMAP store — caller is responsible for closing
     * @throws MessagingException      if the connection or authentication fails
     * @throws IllegalStateException   if IMAP is not configured on the account
     */
    @Retry(name = "imap")
    @CircuitBreaker(name = "smtp")
    public Store openImapStore(EmailAccount account) throws MessagingException {
        if (!account.isReadEnabled() || account.getImapHost() == null) {
            throw new IllegalStateException("IMAP not configured for account: " + account.getId());
        }

        // SSRF / DNS-rebinding guard: re-validate the host at connect time.
        hostSecurityValidator.validateHostAllowed(account.getImapHost());

        String password = encryption.decrypt(account.getImapPassword());
        boolean ssl = Boolean.TRUE.equals(account.getImapSsl());
        String protocol = ssl ? "imaps" : "imap";

        Properties props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".host", account.getImapHost());
        props.put("mail." + protocol + ".port", String.valueOf(account.getImapPort()));
        props.put("mail." + protocol + ".ssl.enable", String.valueOf(ssl));
        props.put("mail." + protocol + ".connectiontimeout", TIMEOUT_CONNECT);
        props.put("mail." + protocol + ".timeout", TIMEOUT_READ);

        Session session = Session.getInstance(props);
        Store store = session.getStore(protocol);
        store.connect(account.getImapHost(), account.getImapPort(),
                account.getImapUsername(), password);
        return store;
    }

    /**
     * Creates an authenticated SMTP {@link Session} for sending emails.
     *
     * @param account the email account with encrypted SMTP credentials
     * @return a configured SMTP session ready for {@link Transport#send(Message)}
     * @throws IllegalStateException if SMTP is not configured on the account
     */
    @CircuitBreaker(name = "smtp")
    public Session createSmtpSession(EmailAccount account) {
        if (!account.isWriteEnabled() || account.getSmtpHost() == null) {
            throw new IllegalStateException("SMTP not configured for account: " + account.getId());
        }

        // SSRF / DNS-rebinding guard: re-validate the host at connect time.
        hostSecurityValidator.validateHostAllowed(account.getSmtpHost());

        String password = encryption.decrypt(account.getSmtpPassword());
        boolean ssl = Boolean.TRUE.equals(account.getSmtpSsl());

        Properties props = new Properties();
        props.put("mail.smtp.host", account.getSmtpHost());
        props.put("mail.smtp.port", String.valueOf(account.getSmtpPort()));
        props.put("mail.smtp.auth", "true");
        if (ssl) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }
        props.put("mail.smtp.connectiontimeout", TIMEOUT_CONNECT);
        props.put("mail.smtp.timeout", TIMEOUT_READ);

        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(account.getSmtpUsername(), password);
            }
        });
    }
}
