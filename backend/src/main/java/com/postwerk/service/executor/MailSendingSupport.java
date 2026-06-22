package com.postwerk.service.executor;

import com.postwerk.model.EmailAccount;
import com.postwerk.service.EmailSyncService;
import com.postwerk.service.MailConnectionFactory;
import com.postwerk.util.SafeStrings;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Component;

/**
 * Shared SMTP sending support for automation executors/processors.
 *
 * <p>Centralizes the duplicated "build a {@link MimeMessage}, send it via {@link Transport},
 * then append a copy to the SENT folder" sequence used by SEND_EMAIL, REPLY and FORWARD, along
 * with the SMTP-readiness guard.</p>
 *
 * @since 1.0
 */
@Component
public class MailSendingSupport {

    private final MailConnectionFactory mailConnectionFactory;
    private final EmailSyncService emailSyncService;

    public MailSendingSupport(MailConnectionFactory mailConnectionFactory, EmailSyncService emailSyncService) {
        this.mailConnectionFactory = mailConnectionFactory;
        this.emailSyncService = emailSyncService;
    }

    /**
     * Ensures the account can send mail; throws if SMTP is not configured.
     */
    public void requireSmtp(EmailAccount account) {
        if (!account.isWriteEnabled() || account.getSmtpHost() == null) {
            throw new IllegalStateException("SMTP not configured for account: " + account.getId());
        }
    }

    /**
     * Builds and sends the message from the given account, then appends a copy to the SENT folder.
     *
     * @return the sent {@link MimeMessage}
     */
    public MimeMessage send(EmailAccount account, OutgoingMail mail) throws MessagingException {
        Session session = mailConnectionFactory.createSmtpSession(account);
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(account.getEmail()));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(mail.to()));

        if (mail.cc() != null && !mail.cc().isBlank()) {
            message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(mail.cc()));
        }
        if (mail.bcc() != null && !mail.bcc().isBlank()) {
            message.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(mail.bcc()));
        }

        message.setSubject(SafeStrings.stripCrlf(mail.subject()));
        if (mail.html()) {
            message.setContent(mail.body(), "text/html; charset=UTF-8");
        } else {
            message.setText(mail.body(), "UTF-8");
        }

        Transport.send(message);
        emailSyncService.appendToSentFolder(account, message);
        return message;
    }
}
