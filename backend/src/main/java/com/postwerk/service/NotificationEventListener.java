package com.postwerk.service;

import com.postwerk.dto.UsageResponse;
import com.postwerk.event.AiUsageRecordedEvent;
import com.postwerk.event.ApprovalPendingEvent;
import com.postwerk.event.AutomationFailedEvent;
import com.postwerk.event.MailboxSyncErrorEvent;
import com.postwerk.event.TeamInvitedEvent;
import com.postwerk.model.enums.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.postwerk.util.MonetaryConstants.MICROS_PER_CENT;

/**
 * Turns decoupled domain events into notifications. Recipient resolution + the in-app/dedup gates live
 * in {@link RecipientResolver} / {@link NotificationService}. APPROVAL runs {@code AFTER_COMMIT} (its
 * producer is transactional — only notify if the park committed); the others run on a plain async
 * listener (their producers persist in their own auto-commit and carry no surrounding transaction).
 * See {@code doc/NOTIFICATION_SYSTEM_DESIGN.md}.
 *
 * @since 1.0
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notificationService;
    private final RecipientResolver recipientResolver;
    private final QuotaService quotaService;

    public NotificationEventListener(NotificationService notificationService,
                                     RecipientResolver recipientResolver,
                                     QuotaService quotaService) {
        this.notificationService = notificationService;
        this.recipientResolver = recipientResolver;
        this.quotaService = quotaService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApprovalPending(ApprovalPendingEvent event) {
        try {
            List<UUID> recipients = recipientResolver.ownerAndOrgAdmins(event.organizationId(), event.ownerUserId());
            if (recipients.isEmpty()) return;

            Map<String, Object> params = new HashMap<>();
            params.put("automationName", event.automationName() != null ? event.automationName() : "");
            params.put("nodeLabel", event.nodeLabel() != null ? event.nodeLabel() : "");

            Map<String, Object> payload = new HashMap<>();
            if (event.pendingActionId() != null) payload.put("pendingActionId", event.pendingActionId().toString());
            if (event.automationId() != null) payload.put("automationId", event.automationId().toString());

            String dedupKey = event.pendingActionId() != null ? "approval:" + event.pendingActionId() : null;

            notificationService.create(recipients, new NewNotification(
                    NotificationType.APPROVAL_PENDING, event.organizationId(), null, null,
                    params, "/dashboard/approvals", payload, dedupKey));
        } catch (Exception e) {
            log.error("Failed to create APPROVAL_PENDING notification for action {}", event.pendingActionId(), e);
        }
    }

    @Async
    @EventListener
    public void onAutomationFailed(AutomationFailedEvent event) {
        try {
            List<UUID> recipients = recipientResolver.ownerAndOrgAdmins(event.organizationId(), event.ownerUserId());
            if (recipients.isEmpty()) return;

            Map<String, Object> params = new HashMap<>();
            params.put("automationName", event.automationName() != null ? event.automationName() : "");

            Map<String, Object> payload = new HashMap<>();
            if (event.automationId() != null) payload.put("automationId", event.automationId().toString());
            if (event.errorMessage() != null) payload.put("error", event.errorMessage());

            // Throttle: at most one per automation per hour per recipient.
            long hourBucket = Instant.now().getEpochSecond() / 3600;
            String dedupKey = event.automationId() != null ? "auto_failed:" + event.automationId() + ":" + hourBucket : null;
            String link = event.automationId() != null
                    ? "/dashboard/automations/" + event.automationId() : "/dashboard/automations";

            notificationService.create(recipients, new NewNotification(
                    NotificationType.AUTOMATION_FAILED, event.organizationId(), null, null,
                    params, link, payload, dedupKey));
        } catch (Exception e) {
            log.error("Failed to create AUTOMATION_FAILED notification for automation {}", event.automationId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamInvited(TeamInvitedEvent event) {
        try {
            if (event.invitedUserId() == null) return;

            Map<String, Object> params = new HashMap<>();
            params.put("orgName", event.organizationName() != null ? event.organizationName() : "");
            params.put("invitedBy", event.invitedByName() != null ? event.invitedByName() : "");

            Map<String, Object> payload = new HashMap<>();
            if (event.organizationId() != null) payload.put("organizationId", event.organizationId().toString());

            // One invite notification per (org, invitee).
            String dedupKey = event.organizationId() != null
                    ? "team_invite:" + event.organizationId() + ":" + event.invitedUserId() : null;

            notificationService.create(List.of(event.invitedUserId()), new NewNotification(
                    NotificationType.TEAM_INVITED, event.organizationId(), null, null,
                    params, "/dashboard", payload, dedupKey));
        } catch (Exception e) {
            log.error("Failed to create TEAM_INVITED notification for user {}", event.invitedUserId(), e);
        }
    }

    @Async
    @EventListener
    public void onAiUsageRecorded(AiUsageRecordedEvent event) {
        try {
            UsageResponse usage = quotaService.getUsage(event.organizationId());
            int limitCents = usage.plan().costLimitCents();
            if (limitCents <= 0) return; // 0 = AI disabled, -1 = unlimited → nothing to warn about

            long limitMicros = (long) limitCents * MICROS_PER_CENT;
            long usedMicros = usage.usage().costUsedMicros();

            NotificationType type;
            String dedupTag;
            if (usedMicros >= limitMicros) {
                type = NotificationType.QUOTA_EXCEEDED;
                dedupTag = "quota_exc";
            } else if (usedMicros * 100 >= limitMicros * 80L) {
                type = NotificationType.QUOTA_WARNING;
                dedupTag = "quota_warn";
            } else {
                return; // below the 80% threshold
            }

            List<UUID> recipients = recipientResolver.ownerAndOrgAdmins(event.organizationId(), null);
            if (recipients.isEmpty()) return;

            int percent = (int) Math.min(100, usedMicros * 100 / limitMicros);
            Map<String, Object> params = new HashMap<>();
            params.put("percent", percent + "%");

            // Once per org per billing month per tier.
            String dedupKey = dedupTag + ":" + event.organizationId() + ":" + YearMonth.now(ZoneOffset.UTC);

            notificationService.create(recipients, new NewNotification(
                    type, event.organizationId(), null, null, params, "/dashboard/plans", Map.of(), dedupKey));
        } catch (Exception e) {
            log.error("Failed to evaluate quota notification for org {}", event.organizationId(), e);
        }
    }

    @Async
    @EventListener
    public void onMailboxSyncError(MailboxSyncErrorEvent event) {
        try {
            List<UUID> recipients = recipientResolver.ownerAndOrgAdmins(event.organizationId(), event.ownerUserId());
            if (recipients.isEmpty()) return;

            NotificationType type = event.authError()
                    ? NotificationType.MAILBOX_AUTH_ERROR : NotificationType.MAILBOX_CONN_ERROR;

            Map<String, Object> params = new HashMap<>();
            params.put("account", event.accountEmail() != null ? event.accountEmail() : "");

            Map<String, Object> payload = new HashMap<>();
            if (event.accountId() != null) payload.put("accountId", event.accountId().toString());

            // At most one per account per day per error class (producer already fires only on transition).
            long dayBucket = Instant.now().getEpochSecond() / 86_400;
            String dedupKey = "mailbox_err:" + event.accountId() + ":" + (event.authError() ? "auth" : "conn") + ":" + dayBucket;

            notificationService.create(recipients, new NewNotification(
                    type, event.organizationId(), null, null, params, "/dashboard/email-accounts", payload, dedupKey));
        } catch (Exception e) {
            log.error("Failed to create mailbox-error notification for account {}", event.accountId(), e);
        }
    }
}
