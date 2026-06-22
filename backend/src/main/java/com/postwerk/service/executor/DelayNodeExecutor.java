package com.postwerk.service.executor;

import com.postwerk.model.AutomationDelayedEmail;
import com.postwerk.model.Email;
import com.postwerk.repository.AutomationDelayedEmailRepository;
import com.postwerk.util.NodeConfigReader;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Executor for the DELAY node that queues emails for deferred processing.
 * In dry-run mode, simulates the delay without persisting.
 *
 * @since 1.0
 */
@Component
public class DelayNodeExecutor {

    private final AutomationDelayedEmailRepository delayedEmailRepository;

    public DelayNodeExecutor(AutomationDelayedEmailRepository delayedEmailRepository) {
        this.delayedEmailRepository = delayedEmailRepository;
    }

    /**
     * In dry-run mode, returns simulated detail. In live mode, queues the email for delayed processing.
     */
    public Map<String, Object> execute(Email email, UUID automationId, UUID nodeId,
                                        JsonNode config, boolean dryRun) {
        int delayMinutes = NodeConfigReader.integer(config, "delayMinutes", 30);
        Instant delayedUntil = Instant.now().plus(delayMinutes, ChronoUnit.MINUTES);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("delayMinutes", delayMinutes);
        detail.put("delayedUntil", delayedUntil.toString());

        if (!dryRun) {
            AutomationDelayedEmail delayed = AutomationDelayedEmail.builder()
                    .emailId(email.getId())
                    .automationId(automationId)
                    .nodeId(nodeId)
                    .delayedUntil(delayedUntil)
                    .processed(false)
                    .build();
            delayedEmailRepository.save(delayed);
        }

        return detail;
    }
}
