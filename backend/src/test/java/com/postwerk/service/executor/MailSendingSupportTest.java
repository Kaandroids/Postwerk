package com.postwerk.service.executor;

import com.postwerk.TestFixtures;
import com.postwerk.model.EmailAccount;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MailSendingSupport#buildMessage} — the MIME assembly, exercised offline
 * with a non-connecting {@link Session} (no real SMTP). Verifies single-part bodies stay single-part
 * and that attachments produce a multipart with the body first followed by one part per attachment.
 */
class MailSendingSupportTest {

    private Session session;
    private EmailAccount account;

    @BeforeEach
    void setUp() {
        session = Session.getInstance(new Properties());
        account = TestFixtures.createEmailAccount(UUID.randomUUID());
    }

    @Test
    void buildMessage_plainText_noAttachments_isSinglePart() throws Exception {
        MimeMessage msg = MailSendingSupport.buildMessage(session, account,
                OutgoingMail.plainText("to@example.com", "Subject", "Hello body"));

        assertThat(msg.getSubject()).isEqualTo("Subject");
        assertThat(msg.getContent()).isInstanceOf(String.class);
        assertThat((String) msg.getContent()).isEqualTo("Hello body");
    }

    @Test
    void buildMessage_withAttachments_isMultipart_bodyPlusOnePartEach() throws Exception {
        OutgoingMail mail = OutgoingMail.plainText("to@example.com", "Invoice", "See attached")
                .withAttachments(List.of(
                        new OutgoingAttachment("invoice.pdf", "application/pdf", new byte[]{1, 2, 3}),
                        new OutgoingAttachment("photo.png", "image/png", new byte[]{4, 5})));

        MimeMessage msg = MailSendingSupport.buildMessage(session, account, mail);

        assertThat(msg.getContent()).isInstanceOf(MimeMultipart.class);
        MimeMultipart mp = (MimeMultipart) msg.getContent();
        assertThat(mp.getCount()).isEqualTo(3); // body + 2 attachments
        assertThat(mp.getBodyPart(1).getFileName()).isEqualTo("invoice.pdf");
        assertThat(mp.getBodyPart(2).getFileName()).isEqualTo("photo.png");
    }

    @Test
    void buildMessage_setsCcAndBcc() throws Exception {
        MimeMessage msg = MailSendingSupport.buildMessage(session, account,
                OutgoingMail.html("to@example.com", "cc@example.com", "bcc@example.com", "Subj", "<p>hi</p>"));

        assertThat(msg.getRecipients(Message.RecipientType.CC)[0].toString()).isEqualTo("cc@example.com");
        assertThat(msg.getRecipients(Message.RecipientType.BCC)[0].toString()).isEqualTo("bcc@example.com");
        assertThat(msg.getContent()).isInstanceOf(String.class); // html body, no attachments -> single part
    }
}
