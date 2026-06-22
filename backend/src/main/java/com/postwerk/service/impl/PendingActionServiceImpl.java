package com.postwerk.service.impl;

import com.postwerk.dto.automation.PendingActionResponse;
import com.postwerk.event.ApprovalPendingEvent;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.AutomationNode;
import com.postwerk.model.Email;
import com.postwerk.model.PendingAction;
import com.postwerk.model.enums.ApprovalStatus;
import com.postwerk.repository.EmailRepository;
import com.postwerk.repository.PendingActionRepository;
import com.postwerk.service.AutomationExecutorService;
import com.postwerk.service.CategoryService;
import com.postwerk.service.PendingActionService;
import com.postwerk.service.executor.ExecutionContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Default {@link PendingActionService}.
 *
 * @since 1.0
 */
@Service
public class PendingActionServiceImpl implements PendingActionService {

    private static final Logger log = LoggerFactory.getLogger(PendingActionServiceImpl.class);

    private final PendingActionRepository repository;
    private final ObjectMapper objectMapper;
    /** Lazy to break the executor &lt;-&gt; pending-action cycle (the executor parks; approval re-executes). */
    private final AutomationExecutorService executor;
    private final CategoryService categoryService;
    private final EmailRepository emailRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PendingActionServiceImpl(PendingActionRepository repository, ObjectMapper objectMapper,
                                    @Lazy AutomationExecutorService executor,
                                    CategoryService categoryService, EmailRepository emailRepository,
                                    ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.categoryService = categoryService;
        this.emailRepository = emailRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public void park(AutomationNode node, Map<String, Object> detail, ExecutionContext context, UUID userId) {
        String detailJson = toJson(detail != null ? detail : Map.of());
        String snapshotJson = toJson(context.getVariables());

        UUID automationId = node.getAutomation() != null ? node.getAutomation().getId() : null;
        UUID emailId = context.getEmail() != null ? context.getEmail().getId() : null;

        PendingAction action = PendingAction.builder()
                .userId(userId)
                .organizationId(context.getOrganizationId())
                .automationId(automationId)
                .emailId(emailId)
                .nodeId(node.getId())
                .nodeType(node.getNodeType())
                .nodeLabel(node.getLabel())
                .actionDetail(detailJson)
                .contextSnapshot(snapshotJson)
                .status(ApprovalStatus.PENDING)
                .build();

        repository.save(action);
        log.info("Parked {} action of automation {} for review (node {})",
                node.getNodeType(), automationId, node.getId());

        // Notify owner + org admins that an approval is waiting (decoupled; fired AFTER_COMMIT).
        String automationName = node.getAutomation() != null ? node.getAutomation().getName() : null;
        eventPublisher.publishEvent(new ApprovalPendingEvent(
                action.getOrganizationId(), userId, automationId, action.getId(), automationName, node.getLabel()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PendingActionResponse> list(UUID organizationId, ApprovalStatus status, Pageable pageable) {
        Page<PendingAction> page = status != null
                ? repository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(organizationId, status, pageable)
                : repository.findByOrganizationIdOrderByCreatedAtDesc(organizationId, pageable);
        return page.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPending(UUID organizationId) {
        return repository.countByOrganizationIdAndStatus(organizationId, ApprovalStatus.PENDING);
    }

    @Override
    @Transactional
    public PendingActionResponse approve(UUID organizationId, UUID deciderUserId, UUID id) {
        PendingAction action = requirePending(organizationId, id);

        boolean ok = executor.executeApprovedAction(action);

        action.setStatus(ApprovalStatus.APPROVED);
        action.setDecidedBy(deciderUserId);
        action.setDecidedAt(Instant.now());
        if (!ok) {
            action.setDecisionNote("Approved, but performing the action reported an error.");
        }
        repository.save(action);
        return toResponse(action);
    }

    @Override
    @Transactional
    public PendingActionResponse reject(UUID organizationId, UUID deciderUserId, UUID id, String note) {
        PendingAction action = requirePending(organizationId, id);
        action.setStatus(ApprovalStatus.REJECTED);
        action.setDecidedBy(deciderUserId);
        action.setDecidedAt(Instant.now());
        action.setDecisionNote(note);
        repository.save(action);
        return toResponse(action);
    }

    @Override
    @Transactional
    public PendingActionResponse reclassify(UUID organizationId, UUID userId, UUID id, UUID correctCategoryId) {
        PendingAction action = requirePending(organizationId, id);

        // Teach the correct category from the source email's text (re-embeds the category).
        if (action.getEmailId() != null) {
            emailRepository.findById(action.getEmailId())
                    .ifPresent(email -> categoryService.addLearningExample(organizationId, userId, correctCategoryId, emailText(email)));
        }

        // The action was triggered by a wrong classification → it should not be performed.
        action.setStatus(ApprovalStatus.REJECTED);
        action.setDecidedBy(userId);
        action.setDecidedAt(Instant.now());
        action.setDecisionNote("Reclassified — taught the correct category.");
        repository.save(action);
        return toResponse(action);
    }

    private String emailText(Email email) {
        StringBuilder sb = new StringBuilder();
        if (email.getSubject() != null) sb.append(email.getSubject()).append('\n');
        String body = email.getBodyText() != null ? email.getBodyText() : email.getBodyHtml();
        if (body != null) sb.append(body.replaceAll("<[^>]*>", " "));
        return sb.toString().strip();
    }

    private PendingAction requirePending(UUID organizationId, UUID id) {
        PendingAction action = repository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("PendingAction", id));
        if (action.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("This action has already been " + action.getStatus().name().toLowerCase() + ".");
        }
        return action;
    }

    private PendingActionResponse toResponse(PendingAction a) {
        return new PendingActionResponse(
                a.getId(), a.getAutomationId(), a.getEmailId(), a.getNodeId(),
                a.getNodeType() != null ? a.getNodeType().name() : null,
                a.getNodeLabel(),
                parseMap(a.getActionDetail()),
                a.getStatus() != null ? a.getStatus().name() : null,
                a.getCreatedAt(), a.getDecidedAt(), a.getDecisionNote(),
                extractTriggerCategory(a.getContextSnapshot()));
    }

    /** Pulls the upstream CATEGORIZE result (category.*) out of the stored context snapshot, if present. */
    private PendingActionResponse.TriggerCategory extractTriggerCategory(String snapshotJson) {
        Map<String, Object> vars = parseMap(snapshotJson);
        Object id = vars.get("category.id");
        Object name = vars.get("category.name");
        if (id == null && name == null) return null;
        Double confidence = null;
        Object c = vars.get("category.confidence");
        if (c instanceof Number n) {
            confidence = n.doubleValue();
        } else if (c != null) {
            try { confidence = Double.parseDouble(c.toString()); } catch (NumberFormatException ignored) { /* leave null */ }
        }
        return new PendingActionResponse.TriggerCategory(
                id != null ? id.toString() : null,
                name != null ? name.toString() : null,
                confidence);
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map != null ? map : Map.of());
        } catch (Exception e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
