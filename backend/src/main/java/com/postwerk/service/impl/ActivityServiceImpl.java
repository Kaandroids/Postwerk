package com.postwerk.service.impl;

import com.postwerk.dto.automation.ActivityEntry;
import com.postwerk.model.Email;
import com.postwerk.model.EmailAutomationTrace;
import com.postwerk.model.EmailNodeTrace;
import com.postwerk.model.enums.NodeType;
import com.postwerk.repository.AutomationRepository;
import com.postwerk.repository.EmailAutomationTraceRepository;
import com.postwerk.service.ActivityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default {@link ActivityService} — surfaces persisted live-run traces as the production activity feed (#3d).
 *
 * @since 1.0
 */
@Service
public class ActivityServiceImpl implements ActivityService {

    private final AutomationRepository automationRepository;
    private final EmailAutomationTraceRepository traceRepository;
    private final ObjectMapper objectMapper;

    public ActivityServiceImpl(AutomationRepository automationRepository,
                               EmailAutomationTraceRepository traceRepository,
                               ObjectMapper objectMapper) {
        this.automationRepository = automationRepository;
        this.traceRepository = traceRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ActivityEntry> getRecent(UUID organizationId, Pageable pageable) {
        List<UUID> automationIds = automationRepository.findByOrganizationId(organizationId).stream()
                .map(a -> a.getId())
                .toList();
        if (automationIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return traceRepository.findByAutomationIdInOrderByStartedAtDesc(automationIds, pageable)
                .map(this::toEntry);
    }

    private ActivityEntry toEntry(EmailAutomationTrace t) {
        Email email = t.getEmail();
        List<ActivityEntry.ActivityStep> steps = t.getNodeTraces().stream()
                .sorted((a, b) -> Integer.compare(a.getExecutionOrder(), b.getExecutionOrder()))
                .map(this::toStep)
                .toList();
        return new ActivityEntry(
                t.getId(), t.getAutomationId(), t.getAutomationName(), t.getAutomationColor(),
                email != null ? email.getSubject() : null,
                email != null ? email.getFromAddress() : null,
                t.getStatus() != null ? t.getStatus().name() : null,
                t.getStartedAt(), t.getCompletedAt(), t.getErrorMessage(),
                steps);
    }

    private ActivityEntry.ActivityStep toStep(EmailNodeTrace n) {
        Map<String, Object> detail = parseDetail(n.getResultDetail());
        return new ActivityEntry.ActivityStep(
                n.getNodeType() != null ? n.getNodeType().name() : null,
                n.getNodeLabel(),
                n.getResultStatus() != null ? n.getResultStatus().name() : null,
                summarize(n.getNodeType(), detail));
    }

    /** A short, language-neutral summary of a node's AI reasoning / action from its result detail. */
    private String summarize(NodeType type, Map<String, Object> d) {
        if (type == null || d == null) return "";
        switch (type) {
            case CATEGORIZE -> {
                Object name = d.get("categoryName") != null ? d.get("categoryName") : d.get("category");
                Object conf = d.get("confidence");
                if (name == null) return "";
                return conf != null ? name + " (" + conf + "%)" : String.valueOf(name);
            }
            case EXTRACT -> {
                Object vals = d.get("extractedValues");
                if (vals instanceof Map<?, ?> m && !m.isEmpty()) {
                    return String.join(", ", m.keySet().stream().map(String::valueOf).toList());
                }
                return "";
            }
            case EMAIL_ACTION -> {
                Object mode = d.getOrDefault("actionMode", "");
                Object target = d.get("toAddress") != null ? d.get("toAddress")
                        : d.get("folder") != null ? d.get("folder")
                        : d.get("templateName");
                return target != null ? mode + " → " + target : String.valueOf(mode);
            }
            case SEND_EMAIL -> {
                return d.get("to") != null ? "→ " + d.get("to") : "";
            }
            case WEBHOOK -> {
                return d.get("url") != null ? String.valueOf(d.get("url")) : "";
            }
            default -> {
                return "";
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseDetail(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
