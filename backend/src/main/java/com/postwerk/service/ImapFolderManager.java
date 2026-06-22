package com.postwerk.service;

import com.postwerk.model.EmailAccount;
import com.postwerk.model.EmailAccountFolder;
import com.postwerk.repository.EmailAccountFolderRepository;
import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages IMAP folder discovery, role detection, persistence and statistics.
 *
 * <p>Extracted from {@code EmailSyncService} to isolate folder-metadata concerns from the
 * email-fetching orchestration. Behaviour is unchanged from the original inline helpers.</p>
 *
 * @since 1.0
 */
@Component
public class ImapFolderManager {

    private static final Logger log = LoggerFactory.getLogger(ImapFolderManager.class);

    private final EmailAccountFolderRepository folderRepository;

    public ImapFolderManager(EmailAccountFolderRepository folderRepository) {
        this.folderRepository = folderRepository;
    }

    /**
     * Lists all IMAP folders that can hold messages and persists them to the database.
     */
    public List<EmailAccountFolder> listAndPersistFolders(Store store, EmailAccount account)
            throws MessagingException {
        Folder defaultFolder = store.getDefaultFolder();
        Folder[] imapFolders = defaultFolder.list("*");

        List<EmailAccountFolder> result = new ArrayList<>();
        for (Folder f : imapFolders) {
            // Skip folders that cannot hold messages
            if ((f.getType() & Folder.HOLDS_MESSAGES) == 0) continue;

            String folderName = f.getFullName();
            String role = detectRole(f);

            EmailAccountFolder entity = folderRepository
                    .findByEmailAccountIdAndName(account.getId(), folderName)
                    .orElseGet(() -> EmailAccountFolder.builder()
                            .emailAccountId(account.getId())
                            .name(folderName)
                            .build());
            entity.setRole(role);
            result.add(folderRepository.save(entity));
        }
        return result;
    }

    /**
     * Updates folder stats (message count, unread count, last synced).
     */
    public void updateFolderStats(Store store, EmailAccount account, EmailAccountFolder folder) {
        try {
            Folder imapFolder = store.getFolder(folder.getName());
            if (!imapFolder.exists()) return;
            imapFolder.open(Folder.READ_ONLY);
            try {
                folder.setMessageCount(imapFolder.getMessageCount());
                folder.setUnreadCount(imapFolder.getUnreadMessageCount());
                folder.setLastSyncedAt(Instant.now());
                folderRepository.save(folder);
            } finally {
                imapFolder.close(false);
            }
        } catch (Exception e) {
            log.debug("Failed to update folder stats for '{}': {}", folder.getName(), e.getMessage());
        }
    }

    /**
     * Finds the SENT folder name for the account by checking persisted folders or IMAP attributes.
     */
    public String findSentFolderName(Store store, EmailAccount account) {
        // First check persisted folders
        var folders = folderRepository.findByEmailAccountIdOrderByRoleAscNameAsc(account.getId());
        for (var f : folders) {
            if ("SENT".equals(f.getRole())) return f.getName();
        }

        // Fallback: detect from IMAP
        try {
            Folder defaultFolder = store.getDefaultFolder();
            Folder[] imapFolders = defaultFolder.list("*");
            for (Folder f : imapFolders) {
                if ((f.getType() & Folder.HOLDS_MESSAGES) == 0) continue;
                String role = detectRole(f);
                if ("SENT".equals(role)) return f.getFullName();
            }
        } catch (Exception e) {
            log.debug("Failed to detect SENT folder from IMAP: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Detects the canonical role of an IMAP folder based on attributes and name.
     */
    public String detectRole(Folder folder) {
        try {
            if (folder instanceof IMAPFolder imapFolder) {
                String[] attrs = imapFolder.getAttributes();
                if (attrs != null) {
                    for (String attr : attrs) {
                        String a = attr.toLowerCase();
                        if (a.contains("\\sent")) return "SENT";
                        if (a.contains("\\junk") || a.contains("\\spam")) return "SPAM";
                        if (a.contains("\\trash")) return "TRASH";
                        if (a.contains("\\drafts")) return "DRAFTS";
                        if (a.contains("\\inbox")) return "INBOX";
                    }
                }
            }
        } catch (Exception e) {
            // Fall through to name-based detection
        }

        // Fallback: name-based detection
        String name = folder.getFullName().toLowerCase();
        if (name.equals("inbox")) return "INBOX";
        if (name.contains("sent") || name.contains("gesendet")) return "SENT";
        if (name.contains("spam") || name.contains("junk")) return "SPAM";
        if (name.contains("trash") || name.contains("papierkorb") || name.contains("deleted")) return "TRASH";
        if (name.contains("draft") || name.contains("entwurf") || name.contains("entwürfe")) return "DRAFTS";
        return "OTHER";
    }
}
