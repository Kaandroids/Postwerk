package com.postwerk.service;

import com.postwerk.config.GdprProperties;
import com.postwerk.model.AuditAction;
import com.postwerk.repository.AiConversationRepository;
import com.postwerk.repository.AuditLogRepository;
import com.postwerk.repository.EmailRepository;
import com.postwerk.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Scheduled service that enforces GDPR-compliant data retention policies.
 * Performs daily soft-deletion and hard-deletion of expired emails, users, and audit logs
 * based on configurable retention periods.
 * <p>
 * All bulk operations are processed in batches of {@link #BATCH_SIZE} rows to avoid
 * long-running transactions and excessive lock contention.
 *
 * @since 1.0
 */
@Service
public class DataRetentionService {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionService.class);
    static final int BATCH_SIZE = 5000;

    private final EmailRepository emailRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final AiConversationRepository conversationRepository;
    private final GdprProperties gdprProperties;
    private final AuditService auditService;
    private final JobRunService jobRunService;

    /** Job registry id (admin Background Jobs). */
    public static final String JOB_ID = "data-retention";

    public DataRetentionService(EmailRepository emailRepository,
                                AuditLogRepository auditLogRepository,
                                UserRepository userRepository,
                                AiConversationRepository conversationRepository,
                                GdprProperties gdprProperties,
                                AuditService auditService,
                                JobRunService jobRunService) {
        this.emailRepository = emailRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.gdprProperties = gdprProperties;
        this.auditService = auditService;
        this.jobRunService = jobRunService;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupExpiredData() {
        jobRunService.run(JOB_ID, this::doCleanup);
    }

    /** Admin "run now" for the retention sweep — records a manual run. */
    public void runNow(java.util.UUID actorUserId) {
        jobRunService.runManual(JOB_ID, this::doCleanup, actorUserId);
    }

    private void doCleanup() {
        try {
            log.info("Data retention: starting scheduled cleanup");

            // Email retention: soft delete old emails, hard delete past grace period
            try {
                Instant emailCutoff = Instant.now().minus(gdprProperties.emailRetentionDays(), ChronoUnit.DAYS);
                log.info("Data retention: soft-deleting emails older than {} ({}d retention)", emailCutoff, gdprProperties.emailRetentionDays());
                int softDeleted = processBatch(() -> emailRepository.softDeleteByReceivedAtBefore(emailCutoff, BATCH_SIZE));
                if (softDeleted > 0) {
                    log.info("Data retention: soft-deleted {} emails", softDeleted);
                    auditService.log(null, AuditAction.DATA_DELETED_BY_RETENTION,
                            "Soft-deleted " + softDeleted + " emails older than " + gdprProperties.emailRetentionDays() + " days", (String) null);
                }

                Instant hardDeleteCutoff = Instant.now().minus(gdprProperties.accountDeletionGraceDays(), ChronoUnit.DAYS);
                int hardDeleted = processBatch(() -> emailRepository.hardDeleteSoftDeletedBefore(hardDeleteCutoff, BATCH_SIZE));
                if (hardDeleted > 0) {
                    log.info("Data retention: hard-deleted {} soft-deleted emails past grace period", hardDeleted);
                }
            } catch (Exception e) {
                log.error("Data retention: email cleanup failed", e);
            }

            // Hard delete users that were soft-deleted beyond grace period
            try {
                Instant hardDeleteCutoff = Instant.now().minus(gdprProperties.accountDeletionGraceDays(), ChronoUnit.DAYS);
                int usersDeleted = processBatch(() -> userRepository.hardDeleteSoftDeletedBefore(hardDeleteCutoff, BATCH_SIZE));
                if (usersDeleted > 0) {
                    log.info("Data retention: hard-deleted {} soft-deleted users past grace period", usersDeleted);
                    auditService.log(null, AuditAction.DATA_DELETED_BY_RETENTION,
                            "Hard-deleted " + usersDeleted + " user accounts past " + gdprProperties.accountDeletionGraceDays() + " day grace period", (String) null);
                }
            } catch (Exception e) {
                log.error("Data retention: user cleanup failed", e);
            }

            // AI conversation retention: soft-delete old conversations, hard-delete past grace period
            try {
                Instant convCutoff = Instant.now().minus(gdprProperties.conversationRetentionDays(), ChronoUnit.DAYS);
                int softDeletedConversations = processBatch(() -> conversationRepository.softDeleteByUpdatedAtBefore(convCutoff, BATCH_SIZE));
                if (softDeletedConversations > 0) {
                    log.info("Data retention: soft-deleted {} AI conversations older than {}d", softDeletedConversations, gdprProperties.conversationRetentionDays());
                    auditService.log(null, AuditAction.DATA_DELETED_BY_RETENTION,
                            "Soft-deleted " + softDeletedConversations + " AI conversations older than " + gdprProperties.conversationRetentionDays() + " days", (String) null);
                }

                Instant hardDeleteCutoff = Instant.now().minus(gdprProperties.accountDeletionGraceDays(), ChronoUnit.DAYS);
                int hardDeletedConversations = processBatch(() -> conversationRepository.hardDeleteSoftDeletedBefore(hardDeleteCutoff, BATCH_SIZE));
                if (hardDeletedConversations > 0) {
                    log.info("Data retention: hard-deleted {} soft-deleted AI conversations past grace period", hardDeletedConversations);
                }
            } catch (Exception e) {
                log.error("Data retention: AI conversation cleanup failed", e);
            }

            // IP pseudonymization: anonymize IPs older than retention period (TTDSG §3)
            try {
                Instant ipCutoff = Instant.now().minus(gdprProperties.ipRetentionDays(), ChronoUnit.DAYS);
                int pseudonymizedAuditIps = processBatch(() -> auditLogRepository.pseudonymizeIpsBefore(ipCutoff, BATCH_SIZE));
                int pseudonymizedLoginIps = processBatch(() -> userRepository.pseudonymizeLoginIpsBefore(ipCutoff, BATCH_SIZE));
                if (pseudonymizedAuditIps > 0 || pseudonymizedLoginIps > 0) {
                    log.info("Data retention: pseudonymized {} audit log IPs and {} user login IPs older than {}d",
                            pseudonymizedAuditIps, pseudonymizedLoginIps, gdprProperties.ipRetentionDays());
                }
            } catch (Exception e) {
                log.error("Data retention: IP pseudonymization failed", e);
            }

            // Audit log retention
            try {
                Instant auditCutoff = Instant.now().minus(gdprProperties.auditLogRetentionDays(), ChronoUnit.DAYS);
                log.info("Data retention: deleting audit logs older than {} ({}d retention)", auditCutoff, gdprProperties.auditLogRetentionDays());
                processBatch(() -> auditLogRepository.deleteOlderThan(auditCutoff, BATCH_SIZE));
            } catch (Exception e) {
                log.error("Data retention: audit log cleanup failed", e);
            }

            log.info("Data retention: scheduled cleanup completed");
        } catch (Exception e) {
            log.error("Data retention: unexpected error during scheduled cleanup", e);
        }
    }

    /**
     * Executes a batch operation in a loop, processing up to {@link #BATCH_SIZE} rows per iteration,
     * until no more rows are affected. Each iteration runs in its own transaction.
     *
     * @param batchOperation a function that processes one batch and returns the number of affected rows
     * @return the total number of rows affected across all batches
     */
    public int processBatch(BatchOperation batchOperation) {
        int totalAffected = 0;
        int affected;
        do {
            affected = batchOperation.execute();
            totalAffected += affected;
        } while (affected >= BATCH_SIZE);
        return totalAffected;
    }

    @FunctionalInterface
    interface BatchOperation {
        int execute();
    }
}
