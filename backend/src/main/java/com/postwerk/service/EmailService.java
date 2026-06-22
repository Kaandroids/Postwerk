package com.postwerk.service;

import com.postwerk.dto.EmailCategoryItem;
import com.postwerk.dto.EmailListResponse;
import com.postwerk.dto.EmailResponse;
import com.postwerk.dto.EmailSyncResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service interface for email operations including paginated listing with advanced filtering,
 * read/star toggling, category assignment, IMAP sync triggering, and attachment downloads.
 *
 * @since 1.0
 */
public interface EmailService {

    Page<EmailListResponse> list(UUID organizationId, UUID accountId, String folder, String query, Boolean isRead,
                                  Instant dateFrom, Instant dateTo,
                                  UUID categoryId, Boolean processed, UUID automationId,
                                  Pageable pageable);

    EmailResponse getById(UUID organizationId, UUID actingUserId, UUID accountId, UUID emailId, String ipAddress);

    EmailResponse markRead(UUID organizationId, UUID accountId, UUID emailId, boolean read);

    EmailResponse toggleStar(UUID organizationId, UUID accountId, UUID emailId);

    EmailResponse assignCategories(UUID organizationId, UUID accountId, UUID emailId, List<EmailCategoryItem> categories);

    EmailResponse reprocess(UUID organizationId, UUID accountId, UUID emailId);

    EmailSyncResponse sync(UUID organizationId, UUID actingUserId, UUID accountId, String ipAddress);

    Object[] downloadAttachment(UUID organizationId, UUID accountId, UUID emailId, int attachmentIndex);

    /**
     * Moves an email to Trash (sets {@code trashedAt}). If the email is already in Trash, it is
     * permanently (soft-)deleted instead.
     */
    void deleteEmail(UUID organizationId, UUID accountId, UUID emailId);

    /** Restores a trashed email back to its original folder (clears {@code trashedAt}). */
    void restoreEmail(UUID organizationId, UUID accountId, UUID emailId);

    /** Permanently (soft-)deletes all trashed emails in the mailbox. Returns the number removed. */
    int emptyTrash(UUID organizationId, UUID accountId);
}
