package com.postwerk.service;

import com.postwerk.model.Email;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMultipart;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Parses JavaMail {@link Message} objects into {@link Email} entities and extracts
 * MIME body/attachment data.
 *
 * <p>Extracted from {@code EmailSyncService} to isolate the pure MIME-parsing concern
 * from IMAP connection and persistence orchestration. All methods are stateless and
 * depend only on their arguments; behaviour is unchanged from the original inline helpers.</p>
 *
 * @since 1.0
 */
@Component
public class MimeMessageParser {

    /**
     * Parses an incoming IMAP message into an {@link Email} entity for the given folder.
     */
    public Email parseMessage(Message msg, UUID accountId, long uid, String folderName)
            throws MessagingException, IOException {
        String messageId = msg.getHeader("Message-ID") != null && msg.getHeader("Message-ID").length > 0
                ? msg.getHeader("Message-ID")[0] : UUID.randomUUID().toString();

        Address[] fromAddrs = msg.getFrom();
        String fromAddress = "";
        String fromPersonal = "";
        if (fromAddrs != null && fromAddrs.length > 0 && fromAddrs[0] instanceof InternetAddress ia) {
            fromAddress = ia.getAddress() != null ? ia.getAddress() : "";
            fromPersonal = ia.getPersonal() != null ? ia.getPersonal() : "";
        }

        String toAddresses = addressesToString(msg.getRecipients(Message.RecipientType.TO));
        String ccAddresses = addressesToString(msg.getRecipients(Message.RecipientType.CC));

        String subject = msg.getSubject() != null ? msg.getSubject() : "";

        String[] bodyParts = extractBody(msg);
        String bodyText = bodyParts[0];
        String bodyHtml = bodyParts[1];

        String snippet = bodyText != null && !bodyText.isBlank()
                ? bodyText.substring(0, Math.min(200, bodyText.length())).replaceAll("\\s+", " ").trim()
                : "";

        Instant receivedAt = msg.getReceivedDate() != null
                ? msg.getReceivedDate().toInstant()
                : (msg.getSentDate() != null ? msg.getSentDate().toInstant() : Instant.now());

        boolean isRead = msg.isSet(Flags.Flag.SEEN);
        String attachmentsJson = collectAttachments(msg);
        boolean hasAttachments = attachmentsJson != null && !attachmentsJson.equals("[]");
        int size = msg.getSize();

        return Email.builder()
                .emailAccountId(accountId)
                .messageId(messageId)
                .folder(folderName)
                .fromAddress(fromAddress)
                .fromPersonal(fromPersonal)
                .toAddresses(toAddresses)
                .ccAddresses(ccAddresses)
                .subject(subject)
                .bodyText(bodyText)
                .bodyHtml(bodyHtml)
                .snippet(snippet)
                .receivedAt(receivedAt)
                .isRead(isRead)
                .isStarred(false)
                .hasAttachments(hasAttachments)
                .attachments(attachmentsJson)
                .sizeBytes(size > 0 ? (long) size : null)
                .uid(uid)
                .build();
    }

    /**
     * Parses an outgoing MimeMessage into an Email entity for the SENT folder.
     */
    public Email parseSentMessage(Message message, UUID accountId, String folderName)
            throws MessagingException, IOException {
        String messageId = message.getHeader("Message-ID") != null && message.getHeader("Message-ID").length > 0
                ? message.getHeader("Message-ID")[0] : UUID.randomUUID().toString();

        Address[] fromAddrs = message.getFrom();
        String fromAddress = "";
        String fromPersonal = "";
        if (fromAddrs != null && fromAddrs.length > 0 && fromAddrs[0] instanceof InternetAddress ia) {
            fromAddress = ia.getAddress() != null ? ia.getAddress() : "";
            fromPersonal = ia.getPersonal() != null ? ia.getPersonal() : "";
        }

        String toAddresses = addressesToString(message.getRecipients(Message.RecipientType.TO));
        String ccAddresses = addressesToString(message.getRecipients(Message.RecipientType.CC));
        String subject = message.getSubject() != null ? message.getSubject() : "";

        String bodyText = null;
        String bodyHtml = null;
        Object content = message.getContent();
        if (content instanceof String s) {
            if (message.isMimeType("text/html")) {
                bodyHtml = s;
            } else {
                bodyText = s;
            }
        } else if (content instanceof MimeMultipart mp) {
            String[] parts = extractMultipart(mp);
            bodyText = parts[0];
            bodyHtml = parts[1];
        }

        String snippet = bodyText != null && !bodyText.isBlank()
                ? bodyText.substring(0, Math.min(200, bodyText.length())).replaceAll("\\s+", " ").trim()
                : "";

        return Email.builder()
                .emailAccountId(accountId)
                .messageId(messageId)
                .folder(folderName)
                .fromAddress(fromAddress)
                .fromPersonal(fromPersonal)
                .toAddresses(toAddresses)
                .ccAddresses(ccAddresses)
                .subject(subject)
                .bodyText(bodyText)
                .bodyHtml(bodyHtml)
                .snippet(snippet)
                .receivedAt(Instant.now())
                .isRead(true)
                .isStarred(false)
                .hasAttachments(false)
                .attachments("[]")
                .uid(0L)
                .processed(true)
                .build();
    }

    /**
     * Collects all attachment {@link BodyPart}s (including nested multiparts) from a multipart message.
     */
    public void collectAttachmentParts(MimeMultipart mp, List<BodyPart> parts)
            throws MessagingException, IOException {
        for (int i = 0; i < mp.getCount(); i++) {
            BodyPart bp = mp.getBodyPart(i);
            if (bp.getFileName() != null || Part.ATTACHMENT.equalsIgnoreCase(bp.getDisposition())) {
                parts.add(bp);
            } else if (bp.getContent() instanceof MimeMultipart nested) {
                collectAttachmentParts(nested, parts);
            }
        }
    }

    /**
     * Returns a JSON array string describing the message's attachments (name/size/contentType).
     */
    public String collectAttachments(Message msg) throws MessagingException, IOException {
        List<String> entries = new ArrayList<>();
        Object content = msg.getContent();
        if (content instanceof MimeMultipart mp) {
            collectAttachmentsFromMultipart(mp, entries);
        }
        if (entries.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(entries.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    private String[] extractBody(Message msg) throws MessagingException, IOException {
        String text = null;
        String html = null;

        Object content = msg.getContent();
        if (content instanceof String s) {
            if (msg.isMimeType("text/html")) {
                html = s;
            } else {
                text = s;
            }
        } else if (content instanceof MimeMultipart mp) {
            String[] parts = extractMultipart(mp);
            text = parts[0];
            html = parts[1];
        }

        return new String[]{text, html};
    }

    private String[] extractMultipart(MimeMultipart mp) throws MessagingException, IOException {
        String text = null;
        String html = null;

        for (int i = 0; i < mp.getCount(); i++) {
            BodyPart bp = mp.getBodyPart(i);
            if (bp.isMimeType("text/plain") && text == null) {
                text = (String) bp.getContent();
            } else if (bp.isMimeType("text/html") && html == null) {
                html = (String) bp.getContent();
            } else if (bp.getContent() instanceof MimeMultipart nested) {
                String[] parts = extractMultipart(nested);
                if (text == null) text = parts[0];
                if (html == null) html = parts[1];
            }
        }

        return new String[]{text, html};
    }

    private void collectAttachmentsFromMultipart(MimeMultipart mp, List<String> entries)
            throws MessagingException, IOException {
        for (int i = 0; i < mp.getCount(); i++) {
            BodyPart bp = mp.getBodyPart(i);
            String fileName = bp.getFileName();
            if (fileName != null || Part.ATTACHMENT.equalsIgnoreCase(bp.getDisposition())) {
                String name = fileName != null ? fileName : "attachment";
                int size = bp.getSize();
                String sizeStr = size > 0 ? formatSize(size) : "unknown";
                String contentType = bp.getContentType() != null
                        ? bp.getContentType().split(";")[0].trim() : "application/octet-stream";
                entries.add("{\"name\":\"" + escapeJson(name) + "\",\"size\":\"" + sizeStr
                        + "\",\"contentType\":\"" + escapeJson(contentType) + "\"}");
            } else if (bp.getContent() instanceof MimeMultipart nested) {
                collectAttachmentsFromMultipart(nested, entries);
            }
        }
    }

    private String formatSize(int bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private String addressesToString(Address[] addresses) {
        if (addresses == null || addresses.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (Address a : addresses) {
            if (!sb.isEmpty()) sb.append(", ");
            if (a instanceof InternetAddress ia) {
                sb.append(ia.getAddress());
            } else {
                sb.append(a.toString());
            }
        }
        return sb.toString();
    }
}
