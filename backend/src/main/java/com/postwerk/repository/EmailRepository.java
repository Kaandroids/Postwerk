package com.postwerk.repository;

import com.postwerk.model.Email;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Email} entities.
 * Provides paginated inbox queries, full-text search, multi-criteria filtering, IMAP UID tracking,
 * and soft-delete lifecycle management for synced email messages.
 *
 * @since 1.0
 */
public interface EmailRepository extends JpaRepository<Email, UUID> {

    /**
     * Explicit projection column list for the inbox-list queries — deliberately excludes the heavy
     * {@code body_text}/{@code body_html} TEXT columns (never used by {@link EmailListView}/EmailListResponse).
     * Each column is aliased to the matching {@link EmailListView} getter property so the native-query
     * interface projection binds reliably. The single-email detail path keeps loading full bodies.
     */
    String EMAIL_LIST_COLUMNS =
            "e.id AS id, " +
            "e.message_id AS messageId, " +
            "e.folder AS folder, " +
            "e.from_address AS fromAddress, " +
            "e.from_personal AS fromPersonal, " +
            "e.to_addresses AS toAddresses, " +
            "e.cc_addresses AS ccAddresses, " +
            "e.subject AS subject, " +
            "e.snippet AS snippet, " +
            "e.received_at AS receivedAt, " +
            "e.is_read AS isRead, " +
            "e.is_starred AS isStarred, " +
            "e.has_attachments AS hasAttachments, " +
            "CAST(e.attachments AS TEXT) AS attachments, " +
            "e.size_bytes AS sizeBytes, " +
            "CAST(e.categories AS TEXT) AS categories, " +
            "e.approval_status AS approvalStatus, " +
            "e.processed AS processed";

    Page<Email> findByEmailAccountId(UUID emailAccountId, Pageable pageable);

    Page<Email> findByEmailAccountIdAndIsRead(UUID emailAccountId, boolean isRead, Pageable pageable);

    Optional<Email> findByIdAndEmailAccountId(UUID id, UUID emailAccountId);

    Optional<Email> findByEmailAccountIdAndMessageId(UUID emailAccountId, String messageId);

    List<Email> findAllByEmailAccountIdIn(List<UUID> accountIds);

    /**
     * Paged, body-free metadata projection for the GDPR data export ([L10]) — pages through emails in
     * bounded batches via {@link EmailExportView} (a closed interface projection, so Hibernate selects
     * only the projected columns and never materializes {@code body_text}/{@code body_html}).
     */
    Page<EmailExportView> findByEmailAccountIdInOrderByReceivedAtDesc(Collection<UUID> accountIds, Pageable pageable);

    void deleteByReceivedAtBefore(Instant cutoff);

    @Modifying
    @Query(value = "UPDATE emails SET deleted_at = NOW() WHERE id IN (SELECT id FROM emails WHERE received_at < :cutoff AND deleted_at IS NULL LIMIT :batchSize)", nativeQuery = true)
    int softDeleteByReceivedAtBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);

    @Modifying
    @Query(value = "DELETE FROM emails WHERE id IN (SELECT id FROM emails WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff LIMIT :batchSize)", nativeQuery = true)
    int hardDeleteSoftDeletedBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);

    List<Email> findByEmailAccountIdAndReceivedAtAfter(UUID emailAccountId, Instant since);

    List<Email> findTop100ByEmailAccountIdAndProcessedFalseOrderByReceivedAtDesc(UUID emailAccountId);

    @Query("SELECT MAX(e.uid) FROM Email e WHERE e.emailAccountId = :accountId")
    Optional<Long> findMaxUidByEmailAccountId(@Param("accountId") UUID accountId);

    @Query("SELECT MAX(e.uid) FROM Email e WHERE e.emailAccountId = :accountId AND e.folder = :folder")
    Optional<Long> findMaxUidByEmailAccountIdAndFolder(@Param("accountId") UUID accountId,
                                                        @Param("folder") String folder);

    @Query(value = "SELECT " + EMAIL_LIST_COLUMNS + " FROM emails e WHERE e.deleted_at IS NULL " +
           "AND e.email_account_id = :accountId " +
           "AND (LOWER(e.subject) LIKE LOWER('%' || CAST(:query AS TEXT) || '%') " +
           "OR LOWER(e.from_address) LIKE LOWER('%' || CAST(:query AS TEXT) || '%') " +
           "OR LOWER(e.from_personal) LIKE LOWER('%' || CAST(:query AS TEXT) || '%')) " +
           "ORDER BY e.received_at DESC",
           countQuery = "SELECT COUNT(*) FROM emails e WHERE e.deleted_at IS NULL " +
           "AND e.email_account_id = :accountId " +
           "AND (LOWER(e.subject) LIKE LOWER('%' || CAST(:query AS TEXT) || '%') " +
           "OR LOWER(e.from_address) LIKE LOWER('%' || CAST(:query AS TEXT) || '%') " +
           "OR LOWER(e.from_personal) LIKE LOWER('%' || CAST(:query AS TEXT) || '%'))",
           nativeQuery = true)
    Page<EmailListView> searchByQuery(@Param("accountId") UUID accountId,
                              @Param("query") String query,
                              Pageable pageable);

    @Query(value = "SELECT " + EMAIL_LIST_COLUMNS + " FROM emails e WHERE e.deleted_at IS NULL " +
           "AND e.email_account_id = :accountId " +
           "AND ((CAST(:trashed AS BOOLEAN) IS NULL AND e.trashed_at IS NULL) " +
           "    OR (CAST(:trashed AS BOOLEAN) = FALSE AND e.trashed_at IS NULL) " +
           "    OR (CAST(:trashed AS BOOLEAN) = TRUE AND e.trashed_at IS NOT NULL)) " +
           "AND (CAST(:folder AS TEXT) IS NULL OR e.folder = CAST(:folder AS TEXT)) " +
           "AND (CAST(:query AS TEXT) IS NULL OR (LOWER(e.subject) LIKE LOWER('%' || CAST(:query AS TEXT) || '%') " +
           "    OR LOWER(e.from_address) LIKE LOWER('%' || CAST(:query AS TEXT) || '%') " +
           "    OR LOWER(e.from_personal) LIKE LOWER('%' || CAST(:query AS TEXT) || '%'))) " +
           "AND (CAST(:isRead AS BOOLEAN) IS NULL OR e.is_read = CAST(:isRead AS BOOLEAN)) " +
           "AND (CAST(:dateFrom AS TIMESTAMPTZ) IS NULL OR e.received_at >= CAST(:dateFrom AS TIMESTAMPTZ)) " +
           "AND (CAST(:dateTo AS TIMESTAMPTZ) IS NULL OR e.received_at <= CAST(:dateTo AS TIMESTAMPTZ)) " +
           "AND (CAST(:categoryId AS TEXT) IS NULL OR e.categories @> CAST(CONCAT('[{\"id\":\"', CAST(:categoryId AS TEXT), '\"}]') AS JSONB)) " +
           "AND (CAST(:processed AS BOOLEAN) IS NULL OR " +
           "     (CAST(:processed AS BOOLEAN) = TRUE AND e.id IN (SELECT eat.email_id FROM email_automation_traces eat)) OR " +
           "     (CAST(:processed AS BOOLEAN) = FALSE AND e.id NOT IN (SELECT eat.email_id FROM email_automation_traces eat))) " +
           "AND (CAST(:automationId AS TEXT) IS NULL OR e.id IN (SELECT eat.email_id FROM email_automation_traces eat WHERE eat.automation_id = CAST(CAST(:automationId AS TEXT) AS UUID))) " +
           "ORDER BY e.received_at DESC",
           countQuery = "SELECT COUNT(*) FROM emails e WHERE e.deleted_at IS NULL " +
           "AND e.email_account_id = :accountId " +
           "AND ((CAST(:trashed AS BOOLEAN) IS NULL AND e.trashed_at IS NULL) " +
           "    OR (CAST(:trashed AS BOOLEAN) = FALSE AND e.trashed_at IS NULL) " +
           "    OR (CAST(:trashed AS BOOLEAN) = TRUE AND e.trashed_at IS NOT NULL)) " +
           "AND (CAST(:folder AS TEXT) IS NULL OR e.folder = CAST(:folder AS TEXT)) " +
           "AND (CAST(:query AS TEXT) IS NULL OR (LOWER(e.subject) LIKE LOWER('%' || CAST(:query AS TEXT) || '%') " +
           "    OR LOWER(e.from_address) LIKE LOWER('%' || CAST(:query AS TEXT) || '%') " +
           "    OR LOWER(e.from_personal) LIKE LOWER('%' || CAST(:query AS TEXT) || '%'))) " +
           "AND (CAST(:isRead AS BOOLEAN) IS NULL OR e.is_read = CAST(:isRead AS BOOLEAN)) " +
           "AND (CAST(:dateFrom AS TIMESTAMPTZ) IS NULL OR e.received_at >= CAST(:dateFrom AS TIMESTAMPTZ)) " +
           "AND (CAST(:dateTo AS TIMESTAMPTZ) IS NULL OR e.received_at <= CAST(:dateTo AS TIMESTAMPTZ)) " +
           "AND (CAST(:categoryId AS TEXT) IS NULL OR e.categories @> CAST(CONCAT('[{\"id\":\"', CAST(:categoryId AS TEXT), '\"}]') AS JSONB)) " +
           "AND (CAST(:processed AS BOOLEAN) IS NULL OR " +
           "     (CAST(:processed AS BOOLEAN) = TRUE AND e.id IN (SELECT eat.email_id FROM email_automation_traces eat)) OR " +
           "     (CAST(:processed AS BOOLEAN) = FALSE AND e.id NOT IN (SELECT eat.email_id FROM email_automation_traces eat))) " +
           "AND (CAST(:automationId AS TEXT) IS NULL OR e.id IN (SELECT eat.email_id FROM email_automation_traces eat WHERE eat.automation_id = CAST(CAST(:automationId AS TEXT) AS UUID)))",
           nativeQuery = true)
    Page<EmailListView> findFiltered(@Param("accountId") UUID accountId,
                             @Param("folder") String folder,
                             @Param("query") String query,
                             @Param("isRead") Boolean isRead,
                             @Param("dateFrom") Instant dateFrom,
                             @Param("dateTo") Instant dateTo,
                             @Param("categoryId") UUID categoryId,
                             @Param("processed") Boolean processed,
                             @Param("automationId") UUID automationId,
                             @Param("trashed") Boolean trashed,
                             Pageable pageable);

    /**
     * Empties the Trash for a mailbox: permanently (soft-)deletes every trashed email by setting
     * {@code deleted_at}, which makes them invisible via the entity's {@code @SQLRestriction}.
     * Returns the number of rows affected.
     */
    @Modifying
    @Query(value = "UPDATE emails SET deleted_at = NOW() " +
           "WHERE email_account_id = :accountId AND trashed_at IS NOT NULL AND deleted_at IS NULL",
           nativeQuery = true)
    int emptyTrash(@Param("accountId") UUID accountId);

    /** GDPR data-footprint: emails stored under any mailbox owned by the user. */
    @Query("SELECT COUNT(e) FROM Email e WHERE e.emailAccountId IN " +
           "(SELECT ea.id FROM EmailAccount ea WHERE ea.userId = :userId)")
    long countByUserId(@Param("userId") UUID userId);

    /** GDPR data-footprint: emails stored under any mailbox owned by the organization. */
    @Query("SELECT COUNT(e) FROM Email e WHERE e.emailAccountId IN " +
           "(SELECT ea.id FROM EmailAccount ea WHERE ea.organizationId = :organizationId)")
    long countByOrganizationId(@Param("organizationId") UUID organizationId);

    /** Analytics (#analytics): incoming emails received by the org since a cutoff — the
     *  denominator for the "emails processed % of incoming" KPI. */
    @Query("SELECT COUNT(e) FROM Email e WHERE e.receivedAt >= :since AND e.emailAccountId IN " +
           "(SELECT ea.id FROM EmailAccount ea WHERE ea.organizationId = :organizationId)")
    long countByOrganizationIdSince(@Param("organizationId") UUID organizationId, @Param("since") Instant since);
}
