package com.postwerk.service;

import com.postwerk.event.MailboxSyncErrorEvent;
import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.postwerk.model.EmailAccountFolder;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.EmailRepository;
import jakarta.mail.*;
import java.time.Instant;
import jakarta.mail.internet.MimeMultipart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import org.eclipse.angus.mail.imap.IMAPFolder;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Service responsible for synchronizing emails from IMAP servers.
 *
 * <p>Handles full folder discovery, incremental email sync (via UID tracking),
 * attachment metadata collection, sent folder appending, and folder statistics updates.
 * Uses {@link MailConnectionFactory} for all IMAP connections. A Redis-based distributed
 * lock prevents concurrent sync operations for the same account.</p>
 *
 * <p>MIME parsing is delegated to {@link MimeMessageParser} and folder discovery/role
 * detection/statistics to {@link ImapFolderManager}.</p>
 *
 * @since 1.0
 */
@Service
public class EmailSyncService {

    private static final Logger log = LoggerFactory.getLogger(EmailSyncService.class);
    private static final String SYNC_LOCK_PREFIX = "sync:lock:";
    private static final Duration SYNC_LOCK_TTL = Duration.ofMinutes(5);

    private final EmailRepository emailRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final MailConnectionFactory mailConnectionFactory;
    private final StringRedisTemplate redisTemplate;
    private final MimeMessageParser mimeMessageParser;
    private final ImapFolderManager imapFolderManager;
    private final ApplicationEventPublisher eventPublisher;

    public EmailSyncService(EmailRepository emailRepository,
                            EmailAccountRepository emailAccountRepository,
                            MailConnectionFactory mailConnectionFactory,
                            StringRedisTemplate redisTemplate,
                            MimeMessageParser mimeMessageParser,
                            ImapFolderManager imapFolderManager,
                            ApplicationEventPublisher eventPublisher) {
        this.emailRepository = emailRepository;
        this.emailAccountRepository = emailAccountRepository;
        this.mailConnectionFactory = mailConnectionFactory;
        this.redisTemplate = redisTemplate;
        this.mimeMessageParser = mimeMessageParser;
        this.imapFolderManager = imapFolderManager;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Syncs all IMAP folders for the given account.
     * Lists folders, persists them, then syncs each one.
     */
    public int syncAllFolders(EmailAccount account) {
        try {
            Store store = mailConnectionFactory.openImapStore(account);
            try {
                List<EmailAccountFolder> folders = imapFolderManager.listAndPersistFolders(store, account);
                int totalNew = 0;
                for (EmailAccountFolder folder : folders) {
                    try {
                        int count = syncFolder(store, account, folder.getName());
                        totalNew += count;
                        // Update folder stats
                        imapFolderManager.updateFolderStats(store, account, folder);
                    } catch (Exception e) {
                        log.warn("Failed to sync folder '{}' for account {}: {}",
                                folder.getName(), account.getId(), e.getMessage());
                    }
                }
                recordSyncSuccess(account);
                return totalNew;
            } finally {
                store.close();
            }
        } catch (MessagingException e) {
            recordSyncFailure(account, e);
            throw new RuntimeException("IMAP sync failed: " + e.getMessage(), e);
        }
    }

    /** Marks the mailbox healthy after a successful sync (admin Email Health). Best-effort. */
    private void recordSyncSuccess(EmailAccount account) {
        try {
            account.setLastSyncAt(Instant.now());
            account.setLastSyncStatus("OK");
            account.setLastError(null);
            account.setLastErrorAt(null);
            emailAccountRepository.save(account);
        } catch (Exception e) {
            log.warn("Failed to record sync success for account {}: {}", account.getId(), e.getMessage());
        }
    }

    /**
     * Records a sync failure on the mailbox, classifying auth rejections (bad credentials) apart from
     * connection/TLS errors so the admin Email Health screen can surface "auth error" vs "failing".
     * Best-effort — never masks the original failure.
     */
    private void recordSyncFailure(EmailAccount account, Exception e) {
        try {
            // Capture the prior status BEFORE overwriting so we notify only on a healthy→failing
            // transition (not on every 5-minute poll while the mailbox stays broken).
            String prev = account.getLastSyncStatus();
            boolean auth = e instanceof AuthenticationFailedException
                    || e.getCause() instanceof AuthenticationFailedException;
            account.setLastSyncStatus(auth ? "AUTH_ERROR" : "CONN_ERROR");
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            account.setLastError(msg.length() > 500 ? msg.substring(0, 500) : msg);
            account.setLastErrorAt(Instant.now());
            emailAccountRepository.save(account);

            boolean wasHealthy = prev == null || "OK".equals(prev);
            if (wasHealthy) {
                eventPublisher.publishEvent(new MailboxSyncErrorEvent(
                        account.getOrganizationId(), account.getUserId(), account.getId(), account.getEmail(), auth));
            }
        } catch (Exception ex) {
            log.warn("Failed to record sync failure for account {}: {}", account.getId(), ex.getMessage());
        }
    }

    /**
     * Syncs all folders with distributed lock to prevent concurrent syncs for the same account.
     * Returns 0 immediately if another sync is already in progress.
     */
    public int sync(EmailAccount account) {
        if (account.isPaused()) {
            log.debug("Account {} is paused (staff), skipping sync", account.getId());
            return 0;
        }
        String lockKey = SYNC_LOCK_PREFIX + account.getId();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", SYNC_LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("Sync already in progress for account {}, skipping", account.getId());
            return 0;
        }
        try {
            return syncAllFolders(account);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * Fire-and-forget sync used by the admin "Re-sync now" action so the request returns immediately.
     * Failures are recorded on the mailbox (via {@link #sync}) and swallowed here.
     */
    @Async
    public void syncInBackground(EmailAccount account) {
        try {
            sync(account);
        } catch (Exception e) {
            log.warn("Background re-sync failed for account {}: {}", account.getId(), e.getMessage());
        }
    }

    /**
     * Opens IMAP connection, lists all folders and persists them to the database.
     * Lightweight operation — does not sync emails.
     */
    public void listAndPersistFolders(EmailAccount account) {
        if (!account.isReadEnabled() || account.getImapHost() == null) return;
        try {
            Store store = mailConnectionFactory.openImapStore(account);
            try {
                imapFolderManager.listAndPersistFolders(store, account);
            } finally {
                store.close();
            }
        } catch (MessagingException e) {
            log.warn("Failed to list folders for account {}: {}", account.getId(), e.getMessage());
        }
    }

    /**
     * Syncs a single IMAP folder: fetches new emails since last known UID.
     */
    private int syncFolder(Store store, EmailAccount account, String folderName) throws MessagingException {
        IMAPFolder folder = (IMAPFolder) store.getFolder(folderName);
        if (!folder.exists() || (folder.getType() & Folder.HOLDS_MESSAGES) == 0) return 0;

        folder.open(Folder.READ_ONLY);
        int newCount = 0;

        try {
            long maxUid = emailRepository.findMaxUidByEmailAccountIdAndFolder(account.getId(), folderName)
                    .orElse(0L);
            Message[] messages;

            if (maxUid > 0) {
                messages = folder.getMessagesByUID(maxUid + 1, UIDFolder.LASTUID);
            } else {
                // First sync — respect syncFromDate or default last 30 days
                LocalDate since = account.getSyncFromDate() != null
                        ? account.getSyncFromDate()
                        : LocalDate.now().minusDays(30);
                jakarta.mail.search.SentDateTerm dateTerm =
                        new jakarta.mail.search.SentDateTerm(
                                jakarta.mail.search.ComparisonTerm.GE,
                                Date.from(since.atStartOfDay(ZoneOffset.UTC).toInstant()));
                messages = folder.search(dateTerm);
            }

            if (messages == null || messages.length == 0) {
                return 0;
            }

            // Prefetch envelope data
            FetchProfile fp = new FetchProfile();
            fp.add(FetchProfile.Item.ENVELOPE);
            fp.add(FetchProfile.Item.FLAGS);
            fp.add(UIDFolder.FetchProfileItem.UID);
            folder.fetch(messages, fp);

            for (Message msg : messages) {
                try {
                    long uid = folder.getUID(msg);
                    if (uid <= maxUid) continue;

                    // Check if message already exists (e.g. saved by appendToSentFolder earlier)
                    String msgId = msg.getHeader("Message-ID") != null && msg.getHeader("Message-ID").length > 0
                            ? msg.getHeader("Message-ID")[0] : null;
                    if (msgId != null) {
                        var existing = emailRepository.findByEmailAccountIdAndMessageId(account.getId(), msgId);
                        if (existing.isPresent()) {
                            // Update folder & uid if the email was moved or appended from another path
                            Email ex = existing.get();
                            if (!folderName.equals(ex.getFolder()) || ex.getUid() != uid) {
                                ex.setFolder(folderName);
                                ex.setUid(uid);
                                emailRepository.save(ex);
                                log.debug("Updated existing email folder/uid for messageId={} account={}",
                                        msgId, account.getId());
                            }
                            continue;
                        }
                    }

                    Email email = mimeMessageParser.parseMessage(msg, account.getId(), uid, folder.getFullName());
                    emailRepository.save(email);
                    newCount++;
                } catch (DataIntegrityViolationException e) {
                    // Concurrent insert race — safe to skip
                    log.debug("Skipping duplicate message for account {}", account.getId());
                } catch (Exception e) {
                    log.warn("Failed to parse message for account {}: {}",
                            account.getId(), e.getMessage());
                }
            }
        } finally {
            folder.close(false);
        }

        return newCount;
    }

    /**
     * Downloads an attachment from IMAP by connecting, finding the message by UID in the specified folder,
     * and extracting the attachment at the given index.
     */
    public Object[] downloadAttachment(EmailAccount account, long uid, int attachmentIndex, String folderName) {
        try {
            Store store = mailConnectionFactory.openImapStore(account);
            try {
                IMAPFolder folder = (IMAPFolder) store.getFolder(folderName);
                folder.open(Folder.READ_ONLY);

                try {
                    Message msg = folder.getMessageByUID(uid);
                    if (msg == null) {
                        throw new RuntimeException("Message not found on server");
                    }

                    List<BodyPart> attachmentParts = new ArrayList<>();
                    Object content = msg.getContent();
                    if (content instanceof MimeMultipart mp) {
                        mimeMessageParser.collectAttachmentParts(mp, attachmentParts);
                    }

                    if (attachmentIndex < 0 || attachmentIndex >= attachmentParts.size()) {
                        throw new RuntimeException("Attachment not found");
                    }

                    BodyPart bp = attachmentParts.get(attachmentIndex);
                    String fileName = bp.getFileName() != null ? bp.getFileName() : "attachment";
                    String contentType = bp.getContentType() != null
                            ? bp.getContentType().split(";")[0].trim()
                            : "application/octet-stream";
                    byte[] data = bp.getInputStream().readAllBytes();

                    return new Object[]{fileName, contentType, data};
                } finally {
                    folder.close(false);
                }
            } finally {
                store.close();
            }
        } catch (MessagingException | IOException e) {
            log.error("Failed to download attachment: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to download attachment");
        }
    }

    /**
     * Fetches only attachment metadata (JSON) for a given UID from IMAP.
     */
    public String fetchAttachmentsMetadata(EmailAccount account, long uid, String folderName) {
        try {
            Store store = mailConnectionFactory.openImapStore(account);
            try {
                IMAPFolder folder = (IMAPFolder) store.getFolder(folderName);
                folder.open(Folder.READ_ONLY);
                try {
                    Message msg = folder.getMessageByUID(uid);
                    if (msg == null) return "[]";
                    return mimeMessageParser.collectAttachments(msg);
                } finally {
                    folder.close(false);
                }
            } finally {
                store.close();
            }
        } catch (MessagingException | IOException e) {
            throw new RuntimeException("Failed to fetch attachments metadata: " + e.getMessage(), e);
        }
    }

    /**
     * Creates an IMAP folder on the server, including any missing parent folders.
     * Supports nested paths like "Buchhaltung/Bestellungen" by creating each level.
     */
    public void createImapFolder(EmailAccount account, String folderName) {
        try {
            Store store = mailConnectionFactory.openImapStore(account);
            try {
                // Determine the server's hierarchy separator
                char separator = store.getDefaultFolder().getSeparator();
                // Normalize user-supplied "/" to the server's separator
                String normalizedName = folderName.replace('/', separator);

                // Create each level of the path (e.g. "Buchhaltung" then "Buchhaltung/Bestellungen")
                String[] parts = normalizedName.split(String.valueOf(separator).equals(".")
                        ? "\\." : String.valueOf(separator));
                StringBuilder path = new StringBuilder();
                for (int i = 0; i < parts.length; i++) {
                    if (i > 0) path.append(separator);
                    path.append(parts[i]);

                    Folder folder = store.getFolder(path.toString());
                    if (!folder.exists()) {
                        // Last segment holds messages; intermediate folders hold subfolders
                        int type = (i == parts.length - 1)
                                ? Folder.HOLDS_MESSAGES
                                : (Folder.HOLDS_FOLDERS | Folder.HOLDS_MESSAGES);
                        boolean created = folder.create(type);
                        if (!created) {
                            throw new RuntimeException("Failed to create IMAP folder: " + path);
                        }
                    }
                }
            } finally {
                store.close();
            }
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to create IMAP folder: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes an IMAP folder from the mail server.
     */
    public void deleteImapFolder(EmailAccount account, String folderName) {
        try {
            Store store = mailConnectionFactory.openImapStore(account);
            try {
                char separator = store.getDefaultFolder().getSeparator();
                String normalizedName = folderName.replace('/', separator);
                Folder folder = store.getFolder(normalizedName);
                if (folder.exists()) {
                    if (folder.isOpen()) folder.close(false);
                    folder.delete(true);
                }
            } finally {
                store.close();
            }
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to delete IMAP folder: " + e.getMessage(), e);
        }
    }

    /**
     * Appends a sent message to the account's SENT IMAP folder and saves it to the DB immediately.
     */
    public void appendToSentFolder(EmailAccount account, Message message) {
        String sentFolderName = null;

        // 1. Try to append to IMAP Sent folder
        if (account.isReadEnabled() && account.getImapHost() != null) {
            try {
                Store store = mailConnectionFactory.openImapStore(account);
                try {
                    sentFolderName = imapFolderManager.findSentFolderName(store, account);
                    if (sentFolderName != null) {
                        Folder sentFolder = store.getFolder(sentFolderName);
                        if (!sentFolder.exists()) {
                            sentFolder.create(Folder.HOLDS_MESSAGES);
                        }
                        sentFolder.open(Folder.READ_WRITE);
                        try {
                            message.setFlag(Flags.Flag.SEEN, true);
                            sentFolder.appendMessages(new Message[]{message});
                            log.debug("Appended sent message to '{}' for account {}", sentFolderName, account.getId());
                        } finally {
                            sentFolder.close(false);
                        }
                    } else {
                        log.warn("No SENT folder found for account {}", account.getId());
                    }
                } finally {
                    store.close();
                }
            } catch (Exception e) {
                log.warn("Failed to append message to IMAP SENT folder for account {}: {}", account.getId(), e.getMessage());
            }
        }

        // 2. Save to DB so it appears immediately — but check for duplicates first
        try {
            String folder = sentFolderName != null ? sentFolderName : "SENT";
            String msgId = message.getHeader("Message-ID") != null && message.getHeader("Message-ID").length > 0
                    ? message.getHeader("Message-ID")[0] : null;
            if (msgId != null && emailRepository.findByEmailAccountIdAndMessageId(account.getId(), msgId).isPresent()) {
                log.debug("Sent email already exists in DB for account {}, skipping", account.getId());
            } else {
                Email email = mimeMessageParser.parseSentMessage(message, account.getId(), folder);
                emailRepository.save(email);
                log.debug("Saved sent email to DB for account {}", account.getId());
            }
        } catch (Exception e) {
            log.warn("Failed to save sent email to DB for account {}: {}", account.getId(), e.getMessage());
        }
    }
}
