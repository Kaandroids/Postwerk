package com.postwerk.service.impl;

import com.postwerk.dto.SimulatedAction;
import com.postwerk.dto.TestModeFeedbackRequest;
import com.postwerk.dto.TestModeResultResponse;
import com.postwerk.dto.TestModeStatsResponse;
import com.postwerk.model.*;
import com.postwerk.model.enums.NodeType;
import com.postwerk.model.enums.TestResultFeedback;
import com.postwerk.repository.AutomationRepository;
import com.postwerk.repository.AutomationTestModeResultRepository;
import com.postwerk.repository.EmailRepository;
import com.postwerk.service.TestModeService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class TestModeServiceImpl implements TestModeService {

    private final AutomationTestModeResultRepository resultRepository;
    private final AutomationRepository automationRepository;
    private final EmailRepository emailRepository;
    private final ObjectMapper objectMapper;

    public TestModeServiceImpl(AutomationTestModeResultRepository resultRepository,
                               AutomationRepository automationRepository,
                               EmailRepository emailRepository,
                               ObjectMapper objectMapper) {
        this.resultRepository = resultRepository;
        this.automationRepository = automationRepository;
        this.emailRepository = emailRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void recordTestModeExecution(Automation automation, Email email, EmailAutomationTrace trace) {
        List<SimulatedAction> actions = extractSimulatedActions(trace);

        String actionsJson;
        try {
            actionsJson = objectMapper.writeValueAsString(actions);
        } catch (Exception e) {
            actionsJson = "[]";
        }

        AutomationTestModeResult result = AutomationTestModeResult.builder()
                .automationId(automation.getId())
                .emailId(email.getId())
                .traceId(trace.getId())
                .simulatedActions(actionsJson)
                .build();

        resultRepository.save(result);
    }

    @Override
    public Page<TestModeResultResponse> getResults(UUID organizationId, UUID automationId, String feedbackFilter, Pageable pageable) {
        verifyOwnership(organizationId, automationId);

        Page<AutomationTestModeResult> page;
        if ("PENDING".equalsIgnoreCase(feedbackFilter)) {
            page = resultRepository.findByAutomationIdAndFeedbackIsNullOrderByCreatedAtDesc(automationId, pageable);
        } else if ("CORRECT".equalsIgnoreCase(feedbackFilter)) {
            page = resultRepository.findByAutomationIdAndFeedbackOrderByCreatedAtDesc(automationId, TestResultFeedback.CORRECT, pageable);
        } else if ("INCORRECT".equalsIgnoreCase(feedbackFilter)) {
            page = resultRepository.findByAutomationIdAndFeedbackOrderByCreatedAtDesc(automationId, TestResultFeedback.INCORRECT, pageable);
        } else {
            page = resultRepository.findByAutomationIdOrderByCreatedAtDesc(automationId, pageable);
        }

        return page.map(this::toResponse);
    }

    @Override
    @Transactional
    public TestModeResultResponse submitFeedback(UUID organizationId, UUID automationId, UUID resultId, TestModeFeedbackRequest request) {
        verifyOwnership(organizationId, automationId);

        AutomationTestModeResult result = resultRepository.findById(resultId)
                .orElseThrow(() -> new IllegalArgumentException("Test mode result not found"));

        if (!result.getAutomationId().equals(automationId)) {
            throw new IllegalArgumentException("Result does not belong to this automation");
        }

        result.setFeedback(TestResultFeedback.valueOf(request.feedback().toUpperCase()));
        result.setFeedbackNote(request.note());
        result.setFeedbackAt(Instant.now());
        resultRepository.save(result);

        return toResponse(result);
    }

    @Override
    public TestModeStatsResponse getStats(UUID organizationId, UUID automationId) {
        verifyOwnership(organizationId, automationId);

        long total = resultRepository.countByAutomationId(automationId);
        long correct = resultRepository.countByAutomationIdAndFeedback(automationId, TestResultFeedback.CORRECT);
        long incorrect = resultRepository.countByAutomationIdAndFeedback(automationId, TestResultFeedback.INCORRECT);
        long pending = resultRepository.countByAutomationIdAndFeedbackIsNull(automationId);

        int accuracy = 0;
        long rated = correct + incorrect;
        if (rated > 0) {
            accuracy = (int) Math.round((double) correct / rated * 100);
        }

        return new TestModeStatsResponse(total, correct, incorrect, pending, accuracy);
    }

    @Override
    @Transactional
    public void clearResults(UUID organizationId, UUID automationId) {
        verifyOwnership(organizationId, automationId);
        resultRepository.deleteByAutomationId(automationId);
    }

    private List<SimulatedAction> extractSimulatedActions(EmailAutomationTrace trace) {
        List<SimulatedAction> actions = new ArrayList<>();
        if (trace.getNodeTraces() == null) return actions;

        for (EmailNodeTrace nt : trace.getNodeTraces()) {
            if (!nt.getNodeType().isAction()) continue;

            String description = buildActionDescription(nt);
            actions.add(new SimulatedAction(
                    nt.getNodeType().name(),
                    nt.getNodeLabel() != null ? nt.getNodeLabel() : nt.getNodeType().name(),
                    description
            ));
        }
        return actions;
    }

    private String buildActionDescription(EmailNodeTrace nt) {
        Map<String, Object> detail = parseDetail(nt.getResultDetail());
        return switch (nt.getNodeType()) {
            case EMAIL_ACTION -> buildEmailActionDescription(detail);
            case LABEL -> "Apply label: " + detail.getOrDefault("categoryName", detail.getOrDefault("categoryId", "unknown"));
            case REMOVE_LABEL -> "Remove label: " + detail.getOrDefault("categoryName", detail.getOrDefault("categoryId", "unknown"));
            case WEBHOOK -> "Call webhook: " + detail.getOrDefault("url", "unknown");
            case SEND_EMAIL -> "Send email to: " + detail.getOrDefault("to", "unknown");
            case INTEGRATION_CALL -> "Call integration: " + detail.getOrDefault("integration", "unknown");
            default -> nt.getNodeType().name();
        };
    }

    private String buildEmailActionDescription(Map<String, Object> detail) {
        String actionMode = String.valueOf(detail.getOrDefault("actionMode", "REPLY"));
        return switch (actionMode) {
            case "FORWARD" -> "Forward to: " + detail.getOrDefault("toAddress", "unknown");
            case "MOVE_FOLDER" -> "Move to folder: " + detail.getOrDefault("folder", "unknown");
            default -> "Reply to email" + (detail.containsKey("templateName") ? " using template: " + detail.get("templateName") : "");
        };
    }

    private Map<String, Object> parseDetail(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private TestModeResultResponse toResponse(AutomationTestModeResult result) {
        Email email = emailRepository.findById(result.getEmailId()).orElse(null);

        List<SimulatedAction> actions;
        try {
            actions = objectMapper.readValue(result.getSimulatedActions(), new TypeReference<>() {});
        } catch (Exception e) {
            actions = List.of();
        }

        return new TestModeResultResponse(
                result.getId(),
                result.getEmailId(),
                email != null ? email.getSubject() : null,
                email != null ? email.getFromAddress() : null,
                email != null ? email.getReceivedAt() : null,
                result.getTraceId(),
                actions,
                result.getFeedback() != null ? result.getFeedback().name() : null,
                result.getFeedbackNote(),
                result.getCreatedAt(),
                result.getFeedbackAt()
        );
    }

    private void verifyOwnership(UUID organizationId, UUID automationId) {
        automationRepository.findByIdAndOrganizationId(automationId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Automation not found"));
    }
}
