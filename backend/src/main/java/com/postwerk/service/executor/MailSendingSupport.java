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
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
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
        MimeMessage message = buildMessage(session, account, mail);

        Transport.send(message);
        emailSyncService.appendToSentFolder(account, message);
        return message;
    }

    /**
     * Builds the {@link MimeMessage} for {@code mail} without sending it. When the mail has no
     * attachments the body is set directly (single-part, unchanged behaviour); otherwise the body
     * becomes the first part of a {@link MimeMultipart} followed by one base64 part per attachment.
     * Package-private and side-effect free so the message assembly can be unit-tested.
     */
    static MimeMessage buildMessage(Session session, EmailAccount account, OutgoingMail mail)
            throws MessagingException {
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

        if (mail.attachments().isEmpty()) {
            setBody(message, mail);
        } else {
            MimeMultipart multipart = new MimeMultipart();
            MimeBodyPart bodyPart = new MimeBodyPart();
            setBody(bodyPart, mail);
            multipart.addBodyPart(bodyPart);

            for (OutgoingAttachment att : mail.attachments()) {
                MimeBodyPart attPart = new MimeBodyPart();
                attPart.setFileName(att.filename());
                attPart.setContent(att.data(), att.contentType());
                attPart.setHeader("Content-Transfer-Encoding", "base64");
                multipart.addBodyPart(attPart);
            }
            message.setContent(multipart);
        }

        return message;
    }

    /** Sets the body on a {@link MimeMessage} or {@link MimeBodyPart}, honouring the HTML/plain flag (UTF-8). */
    private static void setBody(jakarta.mail.Part part, OutgoingMail mail) throws MessagingException {
        part.setContent(mail.body(), (mail.html() ? "text/html" : "text/plain") + "; charset=UTF-8");
    }
}
