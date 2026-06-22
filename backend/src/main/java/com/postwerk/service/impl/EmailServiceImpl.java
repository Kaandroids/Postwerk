package com.postwerk.service.impl;

import com.postwerk.dto.*;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.AuditAction;
import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.postwerk.repository.EmailAccountFolderRepository;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.EmailAutomationTraceRepository;
import com.postwerk.repository.EmailListView;
import com.postwerk.repository.EmailRepository;
import com.postwerk.service.AuditService;
import com.postwerk.service.AutomationExecutorService;
import com.postwerk.service.EmailAutomationTraceService;
import com.postwerk.service.EmailService;
import com.postwerk.service.EmailSyncService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link EmailService}.
 *
 * <p>Provides account-scoped email operations including filtered listing with pagination,
 * on-demand attachment metadata backfill from IMAP, read/star toggling, category assignment,
 * IMAP synchronization, and attachment download. Automatically triggers automation processing
 * for unprocessed emails upon detail retrieval.</p>
 *
 * @since 1.0
 */
@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final EmailRepository emailRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final EmailAccountFolderRepository folderRepository;
    private final EmailSyncService emailSyncService;
    private final EmailAutomationTraceService traceService;
    private final EmailAutomationTraceRepository traceRepository;
    private final AutomationExecutorService automationExecutor;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public EmailServiceImpl(EmailRepository emailRepository,
                            EmailAccountRepository emailAccountRepository,
                            EmailAccountFolderRepository folderRepository,
                            EmailSyncService emailSyncService,
                            EmailAutomationTraceService traceService,
                            EmailAutomationTraceRepository traceRepository,
                            AutomationExecutorService automationExecutor,
                            AuditService auditService,
                            ObjectMapper objectMapper) {
        this.emailRepository = emailRepository;
        this.emailAccountRepository = emailAccountRepository;
        this.folderRepository = folderRepository;
        this.emailSyncService = emailSyncService;
        this.traceService = traceService;
        this.traceRepository = traceRepository;
        this.automationExecutor = automationExecutor;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Page<EmailListResponse> list(UUID organizationId, UUID accountId, String folder, String query, Boolean isRead,
                                         Instant dateFrom, Instant dateTo,
                                         UUID categoryId, Boolean processed, UUID automationId,
                                         Pageable pageable) {
        resolveAccount(organizationId, accountId);

        String q = (query != null && !query.isBlank()) ? query : null;
        String f = (folder != null && !folder.isBlank()) ? folder : null;

        // The Trash (Papierkorb) view is an overlay, not a real folder: it lists every trashed email
        // regardless of its original folder. Every other view excludes trashed emails.
        boolean trashView = "TRASH".equalsIgnoreCase(f);
        Boolean trashed = trashView ? Boolean.TRUE : Boolean.FALSE;
        if (trashView) {
            f = null;
        } else if (f != null) {
            // Resolve folder role (e.g. "SENT") to actual IMAP folder name (e.g. "Sent Items")
            f = resolveFolderName(accountId, f);
        }

        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.unsorted());
        Page<EmailListView> page = emailRepository.findFiltered(accountId, f, q, isRead, dateFrom, dateTo,
                categoryId, processed, automationId, trashed, unsorted);

        // Batch-load trace counts for the whole page in a single query (avoids per-row N+1).
        List<UUID> ids = page.getContent().stream().map(EmailListView::getId).toList();
        Map<UUID, Long> traceCounts = ids.isEmpty()
                ? Map.of()
                : traceRepository.countByEmailIdIn(ids).stream()
                    .collect(Collectors.toMap(r -> (UUID) r[0], r -> (Long) r[1]));

        return page.map(v -> toListResponse(v, traceCounts.getOrDefault(v.getId(), 0L).intValue()));
    }

    /**
     * Resolves a folder role (INBOX, SENT, TRASH, etc.) to the actual IMAP folder name.
     * If the value is already an actual folder name, returns it as-is.
     */
    private String resolveFolderName(UUID accountId, String folderOrRole) {
        // Check if it's a known role
        var folders = folderRepository.findByEmailAccountIdOrderByRoleAscNameAsc(accountId);
        for (var f : folders) {
            if (folderOrRole.equals(f.getRole())) {
                return f.getName();
            }
        }
        // Not a role — assume it's already an actual folder name
        return folderOrRole;
    }

    @Override
    @Transactional
    public EmailResponse getById(UUID organizationId, UUID actingUserId, UUID accountId, UUID emailId, String ipAddress) {
        EmailAccount account = resolveAccount(organizationId, accountId);
        Email email = emailRepository.findByIdAndEmailAccountId(emailId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Email", emailId));

        if (email.isHasAttachments() && email.getAttachments() == null && email.getUid() != null) {
            try {
                String attachmentsJson = emailSyncService.fetchAttachmentsMetadata(account, email.getUid(), email.getFolder());
                email.setAttachments(attachmentsJson);
                emailRepository.save(email);
            } catch (Exception e) {
                log.warn("Failed to backfill attachment metadata for email {}: {}", emailId, e.getMessage());
            }
        }

        // Process through automations if not yet processed — dispatched off the request thread (bounded pool,
        // after this read transaction commits) so opening an email never blocks on automation I/O.
        if (!email.isProcessed()) {
            try {
                automationExecutor.scheduleProcessEmail(email.getId());
            } catch (Exception e) {
                log.warn("Failed to schedule email {} for automation processing: {}", emailId, e.getMessage());
            }
        }

        auditService.log(actingUserId, AuditAction.EMAIL_ACCESSED, "Email: " + emailId, ipAddress);
        return toFullResponse(email);
    }

    @Override
    @Transactional
    public EmailResponse markRead(UUID organizationId, UUID accountId, UUID emailId, boolean read) {
        resolveAccount(organizationId, accountId);
        Email email = emailRepository.findByIdAndEmailAccountId(emailId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Email", emailId));
        email.setRead(read);
        return toFullResponse(emailRepository.save(email));
    }

    @Override
    @Transactional
    public EmailResponse toggleStar(UUID organizationId, UUID accountId, UUID emailId) {
        resolveAccount(organizationId, accountId);
        Email email = emailRepository.findByIdAndEmailAccountId(emailId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Email", emailId));
        email.setStarred(!email.isStarred());
        return toFullResponse(emailRepository.save(email));
    }

    @Override
    @Transactional
    public EmailResponse assignCategories(UUID organizationId, UUID accountId, UUID emailId, List<EmailCategoryItem> categories) {
        resolveAccount(organizationId, accountId);
        Email email = emailRepository.findByIdAndEmailAccountId(emailId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Email", emailId));
        try {
            email.setCategories(objectMapper.writeValueAsString(categories));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize categories", e);
        }
        return toFullResponse(emailRepository.save(email));
    }

    @Override
    @Transactional
    public EmailResponse reprocess(UUID organizationId, UUID accountId, UUID emailId) {
        resolveAccount(organizationId, accountId);
        Email email = emailRepository.findByIdAndEmailAccountId(emailId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Email", emailId));

        // Delete old traces and reset processed flag, then re-run through automations
        traceRepository.deleteByEmailId(emailId);
        email.setProcessed(false);
        email.setCategories(null);
        emailRepository.save(email);
        automationExecutor.processEmail(email);

        return toFullResponse(email);
    }

    @Override
    public EmailSyncResponse sync(UUID organizationId, UUID actingUserId, UUID accountId, String ipAddress) {
        EmailAccount account = resolveAccount(organizationId, accountId);
        int count = emailSyncService.sync(account);
        auditService.log(actingUserId, AuditAction.EMAIL_SYNCED, "Account: " + accountId + ", new: " + count, ipAddress);
        return new EmailSyncResponse(count, Instant.now());
    }

    @Override
    public Object[] downloadAttachment(UUID organizationId, UUID accountId, UUID emailId, int attachmentIndex) {
        EmailAccount account = resolveAccount(organizationId, accountId);
        Email email = emailRepository.findByIdAndEmailAccountId(emailId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Email", emailId));
        if (email.getUid() == null) {
            throw new RuntimeException("Email has no UID — cannot fetch from IMAP");
        }
        return emailSyncService.downloadAttachment(account, email.getUid(), attachmentIndex, email.getFolder());
    }

    @Override
    @Transactional
    public void deleteEmail(UUID organizationId, UUID accountId, UUID emailId) {
        resolveAccount(organizationId, accountId);
        Email email = emailRepository.findByIdAndEmailAccountId(emailId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Email", emailId));
        if (email.getTrashedAt() != null) {
            // Already in Trash → permanent (soft) delete; hidden everywhere by @SQLRestriction.
            email.setDeletedAt(Instant.now());
        } else {
            // First delete → move to Trash (Papierkorb). Stays readable, keeps its folder/uid.
            email.setTrashedAt(Instant.now());
        }
        emailRepository.save(email);
    }

    @Override
    @Transactional
    public void restoreEmail(UUID organizationId, UUID accountId, UUID emailId) {
        resolveAccount(organizationId, accountId);
        Email email = emailRepository.findByIdAndEmailAccountId(emailId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Email", emailId));
        email.setTrashedAt(null);
        emailRepository.save(email);
    }

    @Override
    @Transactional
    public int emptyTrash(UUID organizationId, UUID accountId) {
        resolveAccount(organizationId, accountId);
        return emailRepository.emptyTrash(accountId);
    }

    private EmailAccount resolveAccount(UUID organizationId, UUID accountId) {
        return emailAccountRepository.findByIdAndOrganizationId(accountId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("EmailAccount", accountId));
    }

    private List<EmailCategoryItem> parseCategories(String categoriesJson) {
        if (categoriesJson == null || categoriesJson.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(categoriesJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse categories JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private EmailListResponse toListResponse(EmailListView e, int traceCount) {
        return new EmailListResponse(
                e.getId(), e.getMessageId(), e.getFolder(),
                e.getFromAddress(), e.getFromPersonal(), e.getToAddresses(),
                e.getCcAddresses(), e.getSubject(), e.getSnippet(),
                e.getReceivedAt(), e.getIsRead(), e.getIsStarred(),
                e.getHasAttachments(), e.getAttachments(), e.getSizeBytes(),
                parseCategories(e.getCategories()),
                traceCount,
                e.getApprovalStatus(),
                e.getProcessed()
        );
    }

    private EmailResponse toFullResponse(Email e) {
        List<EmailAutomationTraceResponse> traces = traceService.getTracesByEmailId(e.getId());
        return new EmailResponse(
                e.getId(), e.getMessageId(), e.getFolder(),
                e.getFromAddress(), e.getFromPersonal(), e.getToAddresses(),
                e.getCcAddresses(), e.getSubject(), e.getBodyText(),
                e.getBodyHtml(), e.getSnippet(), e.getReceivedAt(),
                e.isRead(), e.isStarred(), e.isHasAttachments(),
                e.getAttachments(), e.getSizeBytes(),
                parseCategories(e.getCategories()),
                traces.size(),
                e.getApprovalStatus(),
                e.isProcessed(),
                traces
        );
    }
}
