package com.postwerk.service.impl;

import com.postwerk.dto.ComposeEmailRequest;
import com.postwerk.dto.ComposeEmailResponse;
import com.postwerk.dto.DraftAttachmentResponse;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.DraftAttachment;
import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.postwerk.model.EmailAccountFolder;
import com.postwerk.repository.DraftAttachmentRepository;
import com.postwerk.repository.EmailAccountFolderRepository;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.EmailRepository;
import com.postwerk.service.EmailComposeService;
import com.postwerk.service.EmailSyncService;
import com.postwerk.service.MailConnectionFactory;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Default implementation of {@link EmailComposeService}.
 * Handles email composition, SMTP sending, draft persistence, and file attachment management.
 *
 * @since 1.0
 */
@Service
public class EmailComposeServiceImpl implements EmailComposeService {

    private static final Logger log = LoggerFactory.getLogger(EmailComposeServiceImpl.class);
    private static final long MAX_ATTACHMENT_SIZE = 10 * 1024 * 1024; // 10MB per file
    private static final long MAX_DRAFT_ATTACHMENTS_SIZE = 25 * 1024 * 1024; // 25MB cumulative per draft

    /** Executable / script extensions refused as attachments (malware-distribution defense). */
    private static final Set<String> BLOCKED_ATTACHMENT_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "com", "cpl", "scr", "pif", "msi", "msp", "jar",
            "js", "jse", "vbs", "vbe", "wsf", "wsh", "ps1", "psm1", "sh", "bash",
            "dll", "sys", "reg", "hta", "lnk", "gadget", "vb", "jnlp", "app", "deb"
    );

    private final EmailRepository emailRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final EmailAccountFolderRepository folderRepository;
    private final DraftAttachmentRepository draftAttachmentRepository;
    private final MailConnectionFactory mailConnectionFactory;
    private final EmailSyncService emailSyncService;

    public EmailComposeServiceImpl(EmailRepository emailRepository,
                                   EmailAccountRepository emailAccountRepository,
                                   EmailAccountFolderRepository folderRepository,
                                   DraftAttachmentRepository draftAttachmentRepository,
                                   MailConnectionFactory mailConnectionFactory,
                                   EmailSyncService emailSyncService) {
        this.emailRepository = emailRepository;
        this.emailAccountRepository = emailAccountRepository;
        this.folderRepository = folderRepository;
        this.draftAttachmentRepository = draftAttachmentRepository;
        this.mailConnectionFactory = mailConnectionFactory;
        this.emailSyncService = emailSyncService;
    }

    @Override
    @Transactional
    public ComposeEmailResponse send(UUID organizationId, UUID accountId, ComposeEmailRequest request) {
        EmailAccount account = resolveAccount(organizationId, accountId);

        if (!account.isWriteEnabled() || account.getSmtpHost() == null) {
            throw new IllegalStateException("SMTP not configured for account: " + account.getId());
        }

        // If sending from a draft, load its attachments
        List<DraftAttachment> attachments = List.of();
        UUID draftId = null;
        if (request.replyToEmailId() != null && !request.replyToEmailId().isBlank()) {
            try {
                draftId = UUID.fromString(request.replyToEmailId());
                Email draft = emailRepository.findByIdAndEmailAccountId(draftId, accountId).orElse(null);
                if (draft != null && isDraftsFolder(draft.getFolder(), accountId)) {
                    attachments = draftAttachmentRepository.findByEmailId(draftId);
                }
            } catch (IllegalArgumentException ignored) {
                // replyToEmailId is a message-id reference, not a draft ID
            }
        }

        // Check if there's a draft being sent (isDraft was previously saved)
        if (request.isDraft()) {
            return saveDraft(organizationId, accountId, request);
        }

        try {
            Session session = mailConnectionFactory.createSmtpSession(account);
            MimeMessage message = buildMimeMessage(session, account, request, attachments);
            Transport.send(message);

            emailSyncService.appendToSentFolder(account, message);

            // Clean up draft if we were sending from one
            if (draftId != null) {
                draftAttachmentRepository.deleteByEmailId(draftId);
                emailRepository.findByIdAndEmailAccountId(draftId, accountId)
                        .filter(d -> isDraftsFolder(d.getFolder(), accountId))
                        .ifPresent(emailRepository::delete);
            }

            String messageId = message.getMessageID();
            return new ComposeEmailResponse(
                    null, messageId, "SENT",
                    request.to(), request.cc(), request.bcc(),
                    request.subject(), request.body(),
                    Instant.now(), false, List.of()
            );
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public ComposeEmailResponse saveDraft(UUID organizationId, UUID accountId, ComposeEmailRequest request) {
        EmailAccount account = resolveAccount(organizationId, accountId);

        Email draft = Email.builder()
                .emailAccountId(accountId)
                .messageId("draft-" + UUID.randomUUID())
                .folder(resolveDraftsFolderName(accountId))
                .fromAddress(account.getEmail())
                .fromPersonal(account.getDisplayName())
                .toAddresses(request.to())
                .ccAddresses(request.cc())
                .bccAddresses(request.bcc())
                .subject(request.subject())
                .bodyHtml(request.body())
                .snippet(request.subject() != null ? request.subject() : "")
                .inReplyTo(request.inReplyTo())
                .isRead(true)
                .isStarred(false)
                .hasAttachments(false)
                .processed(false)
                .receivedAt(Instant.now())
                .build();

        draft = emailRepository.save(draft);
        return toResponse(draft);
    }

    @Override
    @Transactional
    public ComposeEmailResponse updateDraft(UUID organizationId, UUID accountId, UUID draftId, ComposeEmailRequest request) {
        resolveAccount(organizationId, accountId);
        Email draft = emailRepository.findByIdAndEmailAccountId(draftId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Draft", draftId));

        if (!isDraftsFolder(draft.getFolder(), accountId)) {
            throw new IllegalStateException("Email is not a draft");
        }

        draft.setToAddresses(request.to());
        draft.setCcAddresses(request.cc());
        draft.setBccAddresses(request.bcc());
        draft.setSubject(request.subject());
        draft.setBodyHtml(request.body());
        draft.setInReplyTo(request.inReplyTo());

        List<DraftAttachment> atts = draftAttachmentRepository.findByEmailId(draftId);
        draft.setHasAttachments(!atts.isEmpty());

        draft = emailRepository.save(draft);
        return toResponse(draft);
    }

    @Override
    @Transactional
    public void deleteDraft(UUID organizationId, UUID accountId, UUID draftId) {
        resolveAccount(organizationId, accountId);
        Email draft = emailRepository.findByIdAndEmailAccountId(draftId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Draft", draftId));

        if (!isDraftsFolder(draft.getFolder(), accountId)) {
            throw new IllegalStateException("Email is not a draft");
        }

        draftAttachmentRepository.deleteByEmailId(draftId);
        emailRepository.delete(draft);
    }

    @Override
    @Transactional
    public DraftAttachmentResponse uploadAttachment(UUID organizationId, UUID accountId, UUID draftId, MultipartFile file) {
        resolveAccount(organizationId, accountId);
        Email draft = emailRepository.findByIdAndEmailAccountId(draftId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Draft", draftId));

        if (!isDraftsFolder(draft.getFolder(), accountId)) {
            throw new IllegalStateException("Email is not a draft");
        }

        validateAttachment(file, draftId);

        try {
            DraftAttachment attachment = DraftAttachment.builder()
                    .emailId(draftId)
                    .fileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "attachment")
                    .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                    .sizeBytes(file.getSize())
                    .data(file.getBytes())
                    .build();

            attachment = draftAttachmentRepository.save(attachment);

            draft.setHasAttachments(true);
            emailRepository.save(draft);

            return toAttachmentResponse(attachment);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read attachment data", e);
        }
    }

    @Override
    @Transactional
    public void deleteAttachment(UUID organizationId, UUID accountId, UUID draftId, UUID attachmentId) {
        resolveAccount(organizationId, accountId);
        emailRepository.findByIdAndEmailAccountId(draftId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Draft", draftId));

        DraftAttachment attachment = draftAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", attachmentId));

        if (!attachment.getEmailId().equals(draftId)) {
            throw new IllegalStateException("Attachment does not belong to this draft");
        }

        draftAttachmentRepository.delete(attachment);

        // Update hasAttachments flag
        List<DraftAttachment> remaining = draftAttachmentRepository.findByEmailId(draftId);
        if (remaining.isEmpty()) {
            emailRepository.findById(draftId).ifPresent(draft -> {
                draft.setHasAttachments(false);
                emailRepository.save(draft);
            });
        }
    }

    @Override
    public List<DraftAttachmentResponse> listAttachments(UUID organizationId, UUID accountId, UUID draftId) {
        resolveAccount(organizationId, accountId);
        emailRepository.findByIdAndEmailAccountId(draftId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Draft", draftId));

        return draftAttachmentRepository.findByEmailId(draftId).stream()
                .map(this::toAttachmentResponse)
                .toList();
    }

    private MimeMessage buildMimeMessage(Session session, EmailAccount account,
                                         ComposeEmailRequest request,
                                         List<DraftAttachment> attachments) throws MessagingException {
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(account.getEmail()));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(request.to()));

        if (request.cc() != null && !request.cc().isBlank()) {
            message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(request.cc()));
        }
        if (request.bcc() != null && !request.bcc().isBlank()) {
            message.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(request.bcc()));
        }

        message.setSubject(sanitizeHeaderValue(request.subject()));

        if (request.inReplyTo() != null && !request.inReplyTo().isBlank()) {
            message.setHeader("In-Reply-To", request.inReplyTo());
            message.setHeader("References", request.inReplyTo());
        }

        if (attachments.isEmpty()) {
            message.setContent(request.body() != null ? request.body() : "", "text/html; charset=UTF-8");
        } else {
            MimeMultipart multipart = new MimeMultipart();

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(request.body() != null ? request.body() : "", "text/html; charset=UTF-8");
            multipart.addBodyPart(htmlPart);

            for (DraftAttachment att : attachments) {
                MimeBodyPart attPart = new MimeBodyPart();
                attPart.setFileName(att.getFileName());
                attPart.setContent(att.getData(), att.getContentType());
                attPart.setHeader("Content-Transfer-Encoding", "base64");
                multipart.addBodyPart(attPart);
            }

            message.setContent(multipart);
        }

        return message;
    }

    private String sanitizeHeaderValue(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\r\\n]", "");
    }

    /**
     * Rejects an attachment whose per-file size exceeds {@link #MAX_ATTACHMENT_SIZE}, whose
     * extension is an executable/script type ({@link #BLOCKED_ATTACHMENT_EXTENSIONS}), or that
     * would push the draft's cumulative attachment size past {@link #MAX_DRAFT_ATTACHMENTS_SIZE}.
     */
    private void validateAttachment(MultipartFile file, UUID draftId) {
        if (file.getSize() > MAX_ATTACHMENT_SIZE) {
            throw new IllegalArgumentException("Attachment exceeds the maximum size of 10MB");
        }

        String name = file.getOriginalFilename();
        if (name != null) {
            int dot = name.lastIndexOf('.');
            String ext = dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT).trim() : "";
            if (BLOCKED_ATTACHMENT_EXTENSIONS.contains(ext)) {
                throw new IllegalArgumentException("File type '." + ext + "' is not allowed as an attachment");
            }
        }

        long existing = draftAttachmentRepository.findByEmailId(draftId).stream()
                .mapToLong(DraftAttachment::getSizeBytes)
                .sum();
        if (existing + file.getSize() > MAX_DRAFT_ATTACHMENTS_SIZE) {
            throw new IllegalArgumentException("Total attachments exceed the 25MB per-draft limit");
        }
    }

    /**
     * Resolves the actual IMAP folder name for DRAFTS (e.g. "Drafts", "Entwürfe").
     * Falls back to "DRAFTS" if no IMAP folder with the DRAFTS role is found.
     */
    private String resolveDraftsFolderName(UUID accountId) {
        return folderRepository.findByEmailAccountIdOrderByRoleAscNameAsc(accountId).stream()
                .filter(f -> "DRAFTS".equals(f.getRole()))
                .map(EmailAccountFolder::getName)
                .findFirst()
                .orElse("DRAFTS");
    }

    private boolean isDraftsFolder(String folder, UUID accountId) {
        String draftsFolderName = resolveDraftsFolderName(accountId);
        return draftsFolderName.equals(folder) || "DRAFTS".equals(folder);
    }

    private EmailAccount resolveAccount(UUID organizationId, UUID accountId) {
        return emailAccountRepository.findByIdAndOrganizationId(accountId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("EmailAccount", accountId));
    }

    private ComposeEmailResponse toResponse(Email email) {
        List<DraftAttachmentResponse> atts = draftAttachmentRepository.findByEmailId(email.getId()).stream()
                .map(this::toAttachmentResponse)
                .toList();

        return new ComposeEmailResponse(
                email.getId(),
                email.getMessageId(),
                email.getFolder(),
                email.getToAddresses(),
                email.getCcAddresses(),
                email.getBccAddresses(),
                email.getSubject(),
                email.getBodyHtml(),
                email.getReceivedAt(),
                isDraftsFolder(email.getFolder(), email.getEmailAccountId()),
                atts
        );
    }

    private DraftAttachmentResponse toAttachmentResponse(DraftAttachment att) {
        return new DraftAttachmentResponse(att.getId(), att.getFileName(), att.getContentType(), att.getSizeBytes());
    }
}
