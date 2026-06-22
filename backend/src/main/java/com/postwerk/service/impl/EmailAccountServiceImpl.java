package com.postwerk.service.impl;

import com.postwerk.config.EncryptionConfig;
import com.postwerk.dto.EmailAccountRequest;
import com.postwerk.dto.EmailAccountResponse;
import com.postwerk.dto.FolderResponse;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.util.HostSecurityValidator;
import com.postwerk.util.RepositoryHelper;
import com.postwerk.model.AuditAction;
import com.postwerk.model.EmailAccount;
import com.postwerk.model.EmailAccountFolder;
import com.postwerk.repository.EmailAccountFolderRepository;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.service.AuditService;
import com.postwerk.service.EmailAccountService;
import com.postwerk.service.EmailSyncService;
import com.postwerk.service.QuotaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default implementation of {@link EmailAccountService}.
 *
 * <p>Manages email account CRUD with AES-256-GCM password encryption at rest, IMAP/SMTP
 * credential validation, default account promotion/demotion, and IMAP folder discovery.
 * Mailboxes are owned by the active organization (#4); {@code actingUserId} is recorded as the
 * connector and used for audit + quota. Per-mailbox read/send access is enforced at the controller
 * via {@code OrgContextService.requireMailbox}.</p>
 *
 * @since 1.0
 */
@Service
public class EmailAccountServiceImpl implements EmailAccountService {

    private final EmailAccountRepository repository;
    private final EmailAccountFolderRepository folderRepository;
    private final EncryptionConfig encryption;
    private final AuditService auditService;
    private final EmailSyncService emailSyncService;
    private final QuotaService quotaService;
    private final HostSecurityValidator hostSecurityValidator;

    public EmailAccountServiceImpl(EmailAccountRepository repository,
                                   EmailAccountFolderRepository folderRepository,
                                   EncryptionConfig encryption,
                                   AuditService auditService,
                                   EmailSyncService emailSyncService,
                                   QuotaService quotaService,
                                   HostSecurityValidator hostSecurityValidator) {
        this.repository = repository;
        this.folderRepository = folderRepository;
        this.encryption = encryption;
        this.auditService = auditService;
        this.emailSyncService = emailSyncService;
        this.quotaService = quotaService;
        this.hostSecurityValidator = hostSecurityValidator;
    }

    @Override
    @Transactional
    public EmailAccountResponse create(UUID organizationId, UUID actingUserId, EmailAccountRequest request, String ipAddress) {
        quotaService.checkEmailAccountQuota(organizationId);
        validatePermissions(request);

        if (repository.existsByOrganizationIdAndEmail(organizationId, request.email())) {
            throw new IllegalArgumentException("Email account already exists: " + request.email());
        }

        if (request.isDefault()) {
            clearDefault(organizationId);
        }

        boolean shouldBeDefault = request.isDefault() || repository.findByOrganizationId(organizationId).isEmpty();

        var builder = EmailAccount.builder()
                .userId(actingUserId)
                .organizationId(organizationId)
                .email(request.email())
                .displayName(request.displayName())
                .color(request.color())
                .readEnabled(request.readEnabled())
                .writeEnabled(request.writeEnabled())
                .syncFromDate(request.syncFromDate())
                .isDefault(shouldBeDefault)
                .isActive(true);

        if (request.readEnabled()) {
            builder.imapHost(request.imapHost())
                    .imapPort(request.imapPort())
                    .imapUsername(request.imapUsername())
                    .imapPassword(encryption.encrypt(request.imapPassword()))
                    .imapSsl(request.imapSsl());
        }

        if (request.writeEnabled()) {
            builder.smtpHost(request.smtpHost())
                    .smtpPort(request.smtpPort())
                    .smtpUsername(request.smtpUsername())
                    .smtpPassword(encryption.encrypt(request.smtpPassword()))
                    .smtpSsl(request.smtpSsl());
        }

        var saved = repository.save(builder.build());
        auditService.log(actingUserId, AuditAction.EMAIL_ACCOUNT_CREATED, "Account: " + saved.getEmail(), ipAddress);

        // List IMAP folders immediately so sidebar shows them right away (non-blocking)
        emailSyncService.listAndPersistFolders(saved);

        return toResponse(saved);
    }

    @Override
    public List<EmailAccountResponse> listByOrg(UUID organizationId) {
        return repository.findByOrganizationId(organizationId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public EmailAccountResponse getById(UUID organizationId, UUID accountId) {
        return toResponse(findByOrgAndId(organizationId, accountId));
    }

    @Override
    @Transactional
    public EmailAccountResponse update(UUID organizationId, UUID actingUserId, UUID accountId, EmailAccountRequest request, String ipAddress) {
        validatePermissions(request);

        var account = findByOrgAndId(organizationId, accountId);

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("email", account.getEmail());
        before.put("displayName", account.getDisplayName() != null ? account.getDisplayName() : "");
        before.put("imapHost", account.getImapHost() != null ? account.getImapHost() : "");
        before.put("smtpHost", account.getSmtpHost() != null ? account.getSmtpHost() : "");
        before.put("readEnabled", account.isReadEnabled());
        before.put("writeEnabled", account.isWriteEnabled());
        before.put("isDefault", account.isDefault());

        account.setEmail(request.email());
        account.setDisplayName(request.displayName());
        account.setColor(request.color());
        account.setReadEnabled(request.readEnabled());
        account.setWriteEnabled(request.writeEnabled());
        account.setSyncFromDate(request.syncFromDate());

        if (request.readEnabled()) {
            account.setImapHost(request.imapHost());
            account.setImapPort(request.imapPort());
            account.setImapUsername(request.imapUsername());
            if (request.imapPassword() != null && !request.imapPassword().isBlank()) {
                account.setImapPassword(encryption.encrypt(request.imapPassword()));
            }
            account.setImapSsl(request.imapSsl());
        } else {
            account.setImapHost(null);
            account.setImapPort(null);
            account.setImapUsername(null);
            account.setImapPassword(null);
            account.setImapSsl(null);
        }

        if (request.writeEnabled()) {
            account.setSmtpHost(request.smtpHost());
            account.setSmtpPort(request.smtpPort());
            account.setSmtpUsername(request.smtpUsername());
            if (request.smtpPassword() != null && !request.smtpPassword().isBlank()) {
                account.setSmtpPassword(encryption.encrypt(request.smtpPassword()));
            }
            account.setSmtpSsl(request.smtpSsl());
        } else {
            account.setSmtpHost(null);
            account.setSmtpPort(null);
            account.setSmtpUsername(null);
            account.setSmtpPassword(null);
            account.setSmtpSsl(null);
        }

        if (request.isDefault() && !account.isDefault()) {
            clearDefault(organizationId);
            account.setDefault(true);
        }

        var saved = repository.save(account);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("email", saved.getEmail());
        after.put("displayName", saved.getDisplayName() != null ? saved.getDisplayName() : "");
        after.put("imapHost", saved.getImapHost() != null ? saved.getImapHost() : "");
        after.put("smtpHost", saved.getSmtpHost() != null ? saved.getSmtpHost() : "");
        after.put("readEnabled", saved.isReadEnabled());
        after.put("writeEnabled", saved.isWriteEnabled());
        after.put("isDefault", saved.isDefault());

        auditService.logDiff(actingUserId, AuditAction.EMAIL_ACCOUNT_UPDATED, before, after, "Account: " + saved.getEmail(), ipAddress);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID organizationId, UUID actingUserId, UUID accountId, String ipAddress) {
        var account = findByOrgAndId(organizationId, accountId);
        boolean wasDefault = account.isDefault();
        String email = account.getEmail();
        repository.delete(account);

        if (wasDefault) {
            repository.findByOrganizationId(organizationId).stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setDefault(true);
                        repository.save(next);
                    });
        }

        auditService.log(actingUserId, AuditAction.EMAIL_ACCOUNT_DELETED, "Account: " + email, ipAddress);
    }

    @Override
    @Transactional
    public EmailAccountResponse setDefault(UUID organizationId, UUID accountId) {
        var account = findByOrgAndId(organizationId, accountId);
        clearDefault(organizationId);
        account.setDefault(true);
        return toResponse(repository.save(account));
    }

    @Override
    public List<FolderResponse> listFolders(UUID organizationId, UUID accountId) {
        findByOrgAndId(organizationId, accountId);
        return folderRepository.findByEmailAccountIdOrderByRoleAscNameAsc(accountId)
                .stream()
                .map(this::toFolderResponse)
                .toList();
    }

    @Override
    @Transactional
    public FolderResponse createFolder(UUID organizationId, UUID accountId, String folderName) {
        EmailAccount account = findByOrgAndId(organizationId, accountId);
        emailSyncService.createImapFolder(account, folderName);

        EmailAccountFolder folder = folderRepository.findByEmailAccountIdAndName(accountId, folderName)
                .orElseGet(() -> EmailAccountFolder.builder()
                        .emailAccountId(accountId)
                        .name(folderName)
                        .role("OTHER")
                        .build());
        folder = folderRepository.save(folder);
        return toFolderResponse(folder);
    }

    @Override
    @Transactional
    public void deleteFolder(UUID organizationId, UUID accountId, UUID folderId) {
        EmailAccount account = findByOrgAndId(organizationId, accountId);
        EmailAccountFolder folder = folderRepository.findByIdAndEmailAccountId(folderId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder", folderId));
        if (!"OTHER".equals(folder.getRole())) {
            throw new IllegalArgumentException("Cannot delete system folder");
        }
        emailSyncService.deleteImapFolder(account, folder.getName());
        folderRepository.delete(folder);
    }

    private FolderResponse toFolderResponse(EmailAccountFolder folder) {
        return new FolderResponse(
                folder.getId(), folder.getName(), folder.getRole(),
                folder.getMessageCount() != null ? folder.getMessageCount() : 0,
                folder.getUnreadCount() != null ? folder.getUnreadCount() : 0,
                folder.getLastSyncedAt()
        );
    }

    private void validatePermissions(EmailAccountRequest request) {
        if (request.readEnabled()) {
            if (request.imapHost() == null || request.imapHost().isBlank()) {
                throw new IllegalArgumentException("IMAP host is required when read is enabled");
            }
            if (request.imapUsername() == null || request.imapUsername().isBlank()) {
                throw new IllegalArgumentException("IMAP username is required when read is enabled");
            }
            if (request.imapPassword() == null || request.imapPassword().isBlank()) {
                throw new IllegalArgumentException("IMAP password is required when read is enabled");
            }
            // SSRF guard: reject internal/private/metadata IMAP hosts at persistence time.
            hostSecurityValidator.validateHostAllowed(request.imapHost());
        }
        if (request.writeEnabled()) {
            if (request.smtpHost() == null || request.smtpHost().isBlank()) {
                throw new IllegalArgumentException("SMTP host is required when write is enabled");
            }
            if (request.smtpUsername() == null || request.smtpUsername().isBlank()) {
                throw new IllegalArgumentException("SMTP username is required when write is enabled");
            }
            if (request.smtpPassword() == null || request.smtpPassword().isBlank()) {
                throw new IllegalArgumentException("SMTP password is required when write is enabled");
            }
            // SSRF guard: reject internal/private/metadata SMTP hosts at persistence time.
            hostSecurityValidator.validateHostAllowed(request.smtpHost());
        }
    }

    private EmailAccount findByOrgAndId(UUID organizationId, UUID accountId) {
        return RepositoryHelper.findOrThrow(repository::findByIdAndOrganizationId, accountId, organizationId, "EmailAccount");
    }

    private void clearDefault(UUID organizationId) {
        repository.findByOrganizationIdAndIsDefaultTrue(organizationId)
                .ifPresent(existing -> {
                    existing.setDefault(false);
                    repository.save(existing);
                });
    }

    private EmailAccountResponse toResponse(EmailAccount account) {
        return new EmailAccountResponse(
                account.getId(), account.getEmail(), account.getDisplayName(),
                account.getColor(), account.isReadEnabled(), account.isWriteEnabled(),
                account.getImapHost(), account.getImapPort(),
                account.getSmtpHost(), account.getSmtpPort(),
                account.getSyncFromDate(), account.isDefault(),
                account.isActive(), account.getCreatedAt()
        );
    }
}
