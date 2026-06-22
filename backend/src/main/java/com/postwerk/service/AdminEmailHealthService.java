package com.postwerk.service;

import com.postwerk.dto.admin.AdminMailboxHealthDetailResponse;
import com.postwerk.dto.admin.AdminMailboxHealthResponse;
import com.postwerk.dto.admin.EmailClusterSummaryResponse;
import com.postwerk.dto.admin.EmailHealthKpisResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Platform-staff Email Health: cross-tenant monitoring of every customer IMAP/SMTP mailbox, with
 * gated re-sync / pause / resume actions. Read endpoints require {@code INFRA_VIEW}; mutations require
 * {@code INFRA_MANAGE} (enforced at the controller).
 *
 * @since 1.0
 */
public interface AdminEmailHealthService {

    /**
     * Paginated + filterable mailbox list (filtering/sorting done in-memory over all mailboxes, like
     * the rest of the admin tooling — health is a derived field with no column to query on).
     *
     * @param search  matches email / owner email / org name / mailbox id (blank = all)
     * @param protocol "IMAP" | "SMTP" | null
     * @param health   "ok" | "failing" | "auth_error" | "paused" | null
     * @param server   relay cluster (IMAP host) | null
     * @param sync     "recent" (&lt; 1h) | "stale" (&gt; 24h) | null
     */
    Page<AdminMailboxHealthResponse> listMailboxes(String search, String protocol, String health,
                                                   String server, String sync, Pageable pageable);

    EmailHealthKpisResponse kpis();

    List<EmailClusterSummaryResponse> clusters();

    AdminMailboxHealthDetailResponse getMailbox(UUID mailboxId);

    /** Queues a background re-sync for the mailbox; returns its current row. */
    AdminMailboxHealthResponse resync(UUID mailboxId, UUID actorUserId, String ip);

    AdminMailboxHealthDetailResponse pause(UUID mailboxId, UUID actorUserId, String ip);

    AdminMailboxHealthDetailResponse resume(UUID mailboxId, UUID actorUserId, String ip);
}
