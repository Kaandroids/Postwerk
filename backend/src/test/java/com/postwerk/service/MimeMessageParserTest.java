package com.postwerk.service;

import com.postwerk.model.Email;
import jakarta.mail.Flags;
import jakarta.mail.Message;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MimeMessageParser}. The parser is stateless and depends only on its
 * JavaMail arguments, so every case builds a real in-memory {@link MimeMessage} (no IMAP, no
 * network) and asserts the mapped {@link Email}. Locks the MIME body/attachment extraction and
 * header-mapping behaviour that {@code EmailSyncService} relies on.
 */
class MimeMessageParserTest {

    private static final Session SESSION = Session.getInstance(new Properties());
    private static final UUID ACCOUNT = UUID.randomUUID();

    private final MimeMessageParser parser = new MimeMessageParser();

    private static MimeMessage newMessage() {
        return new MimeMessage(SESSION);
    }

    // ── parseMessage: simple text/plain ──────────────────────────────────

    @Test
    void parsesPlainTextMessageHeadersAndBody() throws Exception {
        MimeMessage msg = newMessage();
        msg.setFrom(new InternetAddress("alice@example.com", "Alice Sender"));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse("bob@example.com, carol@example.com"));
        msg.setRecipients(Message.RecipientType.CC, InternetAddress.parse("dan@example.com"));
        msg.setSubject("Quarterly report");
        msg.setText("Hello there, here is the body.");

        Email email = parser.parseMessage(msg, ACCOUNT, 42L, "INBOX");

        assertThat(email.getEmailAccountId()).isEqualTo(ACCOUNT);
        assertThat(email.getFolder()).isEqualTo("INBOX");
        assertThat(email.getUid()).isEqualTo(42L);
        assertThat(email.getFromAddress()).isEqualTo("alice@example.com");
        assertThat(email.getFromPersonal()).isEqualTo("Alice Sender");
        assertThat(email.getToAddresses()).isEqualTo("bob@example.com, carol@example.com");
        assertThat(email.getCcAddresses()).isEqualTo("dan@example.com");
        assertThat(email.getSubject()).isEqualTo("Quarterly report");
        assertThat(email.getBodyText()).isEqualTo("Hello there, here is the body.");
        assertThat(email.getBodyHtml()).isNull();
        assertThat(email.getSnippet()).isEqualTo("Hello there, here is the body.");
        assertThat(email.isHasAttachments()).isFalse();
        assertThat(email.getAttachments()).isEqualTo("[]");
        assertThat(email.isStarred()).isFalse();
    }

    @Test
    void unreadByDefaultAndReadWhenSeenFlagSet() throws Exception {
        MimeMessage unread = newMessage();
        unread.setFrom(new InternetAddress("a@x.com"));
        unread.setText("body");
        assertThat(parser.parseMessage(unread, ACCOUNT, 1L, "INBOX").isRead()).isFalse();

        MimeMessage read = newMessage();
        read.setFrom(new InternetAddress("a@x.com"));
        read.setText("body");
        read.setFlag(Flags.Flag.SEEN, true);
        assertThat(parser.parseMessage(read, ACCOUNT, 1L, "INBOX").isRead()).isTrue();
    }

    // ── parseMessage: HTML and multipart/alternative ─────────────────────

    @Test
    void parsesHtmlOnlyMessage() throws Exception {
        MimeMessage msg = newMessage();
        msg.setFrom(new InternetAddress("a@x.com"));
        msg.setContent("<p>Hi <b>there</b></p>", "text/html; charset=utf-8");
        msg.saveChanges(); // finalize the Content-Type header

        Email email = parser.parseMessage(msg, ACCOUNT, 1L, "INBOX");

        assertThat(email.getBodyHtml()).isEqualTo("<p>Hi <b>there</b></p>");
        assertThat(email.getBodyText()).isNull();
        assertThat(email.getSnippet()).isEmpty(); // snippet derives from plain text only
    }

    @Test
    void parsesMultipartAlternativeIntoBothBodies() throws Exception {
        MimeBodyPart text = new MimeBodyPart();
        text.setText("plain version");
        MimeBodyPart html = new MimeBodyPart();
        html.setContent("<p>html version</p>", "text/html; charset=utf-8");
        MimeMultipart mp = new MimeMultipart("alternative");
        mp.addBodyPart(text);
        mp.addBodyPart(html);

        MimeMessage msg = newMessage();
        msg.setFrom(new InternetAddress("a@x.com"));
        msg.setContent(mp);
        msg.saveChanges(); // finalize the per-part Content-Type headers

        Email email = parser.parseMessage(msg, ACCOUNT, 1L, "INBOX");

        assertThat(email.getBodyText()).isEqualTo("plain version");
        assertThat(email.getBodyHtml()).isEqualTo("<p>html version</p>");
    }

    // ── parseMessage: attachments ────────────────────────────────────────

    @Test
    void detectsAttachmentsAndDescribesThemAsJson() throws Exception {
        MimeBodyPart body = new MimeBodyPart();
        body.setText("see attached");
        MimeBodyPart attach = new MimeBodyPart();
        attach.setContent("PDF-CONTENT", "application/pdf");
        attach.setFileName("report.pdf");
        attach.setDisposition(Part.ATTACHMENT);
        MimeMultipart mp = new MimeMultipart("mixed");
        mp.addBodyPart(body);
        mp.addBodyPart(attach);

        MimeMessage msg = newMessage();
        msg.setFrom(new InternetAddress("a@x.com"));
        msg.setContent(mp);
        msg.saveChanges(); // finalize the per-part Content-Type headers

        Email email = parser.parseMessage(msg, ACCOUNT, 1L, "INBOX");

        assertThat(email.isHasAttachments()).isTrue();
        assertThat(email.getBodyText()).isEqualTo("see attached");
        assertThat(email.getAttachments())
                .contains("\"name\":\"report.pdf\"")
                .contains("\"contentType\":\"application/pdf\"");
    }

    @Test
    void collectAttachmentsReturnsEmptyArrayForPlainMessage() throws Exception {
        MimeMessage msg = newMessage();
        msg.setFrom(new InternetAddress("a@x.com"));
        msg.setText("no attachments here");

        assertThat(parser.collectAttachments(msg)).isEqualTo("[]");
    }

    // ── header edge cases ────────────────────────────────────────────────

    @Test
    void generatesMessageIdWhenHeaderMissing() throws Exception {
        MimeMessage msg = newMessage(); // never saveChanges() -> no Message-ID header
        msg.setFrom(new InternetAddress("a@x.com"));
        msg.setText("body");

        String messageId = parser.parseMessage(msg, ACCOUNT, 1L, "INBOX").getMessageId();

        assertThat(messageId).isNotBlank();
        assertThat(UUID.fromString(messageId)).isNotNull(); // falls back to a random UUID
    }

    @Test
    void preservesExplicitMessageIdHeader() throws Exception {
        MimeMessage msg = newMessage();
        msg.setFrom(new InternetAddress("a@x.com"));
        msg.setText("body");
        msg.setHeader("Message-ID", "<abc-123@example.com>");

        assertThat(parser.parseMessage(msg, ACCOUNT, 1L, "INBOX").getMessageId())
                .isEqualTo("<abc-123@example.com>");
    }

    @Test
    void emptySubjectWhenMissing() throws Exception {
        MimeMessage msg = newMessage();
        msg.setFrom(new InternetAddress("a@x.com"));
        msg.setText("body");

        assertThat(parser.parseMessage(msg, ACCOUNT, 1L, "INBOX").getSubject()).isEmpty();
    }

    @Test
    void blankFromIsToleratedAsEmptyStrings() throws Exception {
        MimeMessage msg = newMessage(); // no From set
        msg.setText("body");

        Email email = parser.parseMessage(msg, ACCOUNT, 1L, "INBOX");

        assertThat(email.getFromAddress()).isEmpty();
        assertThat(email.getFromPersonal()).isEmpty();
        assertThat(email.getToAddresses()).isEmpty();
    }

    @Test
    void snippetTruncatesToTwoHundredCharsAndCollapsesWhitespace() throws Exception {
        String longBody = "word\n\t  ".repeat(60); // > 200 chars, lots of whitespace
        MimeMessage msg = newMessage();
        msg.setFrom(new InternetAddress("a@x.com"));
        msg.setText(longBody);

        String snippet = parser.parseMessage(msg, ACCOUNT, 1L, "INBOX").getSnippet();

        assertThat(snippet.length()).isLessThanOrEqualTo(200);
        assertThat(snippet).doesNotContain("\n").doesNotContain("\t");
        assertThat(snippet).doesNotContain("  "); // collapsed runs of whitespace
    }

    @Test
    void receivedAtFallsBackToSentDateWhenNoReceivedDate() throws Exception {
        Instant sent = Instant.parse("2026-01-02T03:04:05Z");
        MimeMessage msg = newMessage();
        msg.setFrom(new InternetAddress("a@x.com"));
        msg.setText("body");
        msg.setSentDate(Date.from(sent));

        assertThat(parser.parseMessage(msg, ACCOUNT, 1L, "INBOX").getReceivedAt()).isEqualTo(sent);
    }

    // ── parseSentMessage ─────────────────────────────────────────────────

    @Test
    void parsesSentMessageAsReadAndProcessed() throws Exception {
        MimeMessage msg = newMessage();
        msg.setFrom(new InternetAddress("me@x.com", "Me"));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse("you@x.com"));
        msg.setSubject("Re: hi");
        msg.setText("my reply");

        Email email = parser.parseSentMessage(msg, ACCOUNT, "Sent");

        assertThat(email.getFolder()).isEqualTo("Sent");
        assertThat(email.getFromAddress()).isEqualTo("me@x.com");
        assertThat(email.getToAddresses()).isEqualTo("you@x.com");
        assertThat(email.getBodyText()).isEqualTo("my reply");
        assertThat(email.isRead()).isTrue();
        assertThat(email.isProcessed()).isTrue();
        assertThat(email.isHasAttachments()).isFalse();
        assertThat(email.getAttachments()).isEqualTo("[]");
        assertThat(email.getUid()).isEqualTo(0L);
        assertThat(email.getReceivedAt()).isNotNull();
    }

    @Test
    void parsesSentHtmlMessageBody() throws Exception {
        MimeMessage msg = newMessage();
        msg.setFrom(new InternetAddress("me@x.com"));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse("you@x.com"));
        msg.setContent("<p>reply</p>", "text/html; charset=utf-8");
        msg.saveChanges(); // finalize the Content-Type header

        Email email = parser.parseSentMessage(msg, ACCOUNT, "Sent");

        assertThat(email.getBodyHtml()).isEqualTo("<p>reply</p>");
        assertThat(email.getBodyText()).isNull();
    }
}
