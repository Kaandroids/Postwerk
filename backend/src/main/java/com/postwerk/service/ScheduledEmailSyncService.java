package com.postwerk.service;

import com.postwerk.model.EmailAccount;
import com.postwerk.repository.EmailAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scheduled background service that periodically synchronizes emails from all
 * IMAP-enabled accounts at a configurable interval.
 *
 * @since 1.0
 */
@Service
public class ScheduledEmailSyncService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledEmailSyncService.class);

    private final EmailAccountRepository emailAccountRepository;
    private final EmailSyncService emailSyncService;
    private final JobRunService jobRunService;

    public ScheduledEmailSyncService(EmailAccountRepository emailAccountRepository,
                                      EmailSyncService emailSyncService,
                                      JobRunService jobRunService) {
        this.emailAccountRepository = emailAccountRepository;
        this.emailSyncService = emailSyncService;
        this.jobRunService = jobRunService;
    }

    /** Job registry id (admin Background Jobs). */
    public static final String JOB_ID = "email-sync";

    @Scheduled(fixedDelayString = "${app.sync.interval-ms:300000}", initialDelayString = "${app.sync.initial-delay-ms:60000}")
    public void syncAllAccounts() {
        jobRunService.run(JOB_ID, this::doSyncAllAccounts);
    }

    /** Allows the admin "run now" action to trigger a recorded manual sync. */
    public void runNow(java.util.UUID actorUserId) {
        jobRunService.runManual(JOB_ID, this::doSyncAllAccounts, actorUserId);
    }

    private void doSyncAllAccounts() {
        List<EmailAccount> accounts = emailAccountRepository.findByReadEnabledTrueAndImapHostIsNotNull();

        if (accounts.isEmpty()) {
            return;
        }

        log.info("Scheduled sync starting for {} account(s)", accounts.size());

        int poolSize = Math.min(accounts.size(), 4);
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        AtomicInteger totalNew = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        for (EmailAccount account : accounts) {
            executor.submit(() -> {
                try {
                    int newEmails = emailSyncService.sync(account);
                    if (newEmails > 0) {
                        totalNew.addAndGet(newEmails);
                        log.debug("Synced {} new email(s) for account {}", newEmails, account.getEmail());
                    }
                } catch (Exception e) {
                    failed.incrementAndGet();
                    log.warn("Sync failed for account {} ({}): {}", account.getEmail(), account.getId(), e.getMessage());
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Scheduled sync interrupted");
        }

        if (totalNew.get() > 0 || failed.get() > 0) {
            log.info("Scheduled sync complete: {} new email(s), {} failure(s)", totalNew.get(), failed.get());
        }
    }
}
