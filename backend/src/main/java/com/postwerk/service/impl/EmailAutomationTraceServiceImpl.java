package com.postwerk.service.impl;

import com.postwerk.dto.EmailAutomationTraceResponse;
import com.postwerk.dto.EmailNodeTraceResponse;
import com.postwerk.model.*;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.model.enums.TraceStatus;
import com.postwerk.repository.EmailAutomationTraceRepository;
import com.postwerk.repository.EmailNodeTraceRepository;
import com.postwerk.service.EmailAutomationTraceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default implementation of {@link EmailAutomationTraceService}.
 *
 * <p>Manages the lifecycle of automation execution traces, including trace creation,
 * per-node result recording, and completion status updates. Provides read access
 * to historical trace data for diagnostics and UI display.</p>
 *
 * @since 1.0
 */
@Service
public class EmailAutomationTraceServiceImpl implements EmailAutomationTraceService {

    private static final Logger log = LoggerFactory.getLogger(EmailAutomationTraceServiceImpl.class);

    private final EmailAutomationTraceRepository traceRepository;
    private final EmailNodeTraceRepository nodeTraceRepository;
    private final ObjectMapper objectMapper;

    public EmailAutomationTraceServiceImpl(EmailAutomationTraceRepository traceRepository,
                                            EmailNodeTraceRepository nodeTraceRepository,
                                            ObjectMapper objectMapper) {
        this.traceRepository = traceRepository;
        this.nodeTraceRepository = nodeTraceRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public EmailAutomationTrace startTrace(Email email, Automation automation, UUID executionId) {
        var trace = EmailAutomationTrace.builder()
                .email(email)
                .automationId(automation.getId())
                .automationExecutionId(executionId)
                .automationName(automation.getName())
                .automationColor(automation.getColor())
                .startedAt(Instant.now())
                .status(TraceStatus.RUNNING)
                .build();
        return traceRepository.save(trace);
    }

    @Override
    @Transactional
    public EmailNodeTrace addNodeTrace(EmailAutomationTrace trace, AutomationNode node,
                                        NodeResultStatus resultStatus, Map<String, Object> resultDetail) {
        String detailJson = null;
        if (resultDetail != null && !resultDetail.isEmpty()) {
            try {
                detailJson = objectMapper.writeValueAsString(resultDetail);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize node trace detail: {}", e.getMessage());
                detailJson = "{}";
            }
        }

        int order = trace.getNodeTraces() != null ? trace.getNodeTraces().size() : 0;

        var nodeTrace = EmailNodeTrace.builder()
                .trace(trace)
                .nodeId(node.getId())
                .nodeType(node.getNodeType())
                .nodeLabel(node.getLabel())
                .executionOrder(order)
                .resultStatus(resultStatus)
                .resultDetail(detailJson)
                .executedAt(Instant.now())
                .build();
        nodeTrace = nodeTraceRepository.save(nodeTrace);
        trace.getNodeTraces().add(nodeTrace);
        return nodeTrace;
    }

    @Override
    @Transactional
    public void completeTrace(EmailAutomationTrace trace, String status, String errorMessage) {
        trace.setStatus(TraceStatus.valueOf(status));
        trace.setCompletedAt(Instant.now());
        trace.setErrorMessage(errorMessage);
        traceRepository.save(trace);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmailAutomationTraceResponse> getTracesByEmailId(UUID emailId) {
        List<EmailAutomationTrace> traces = traceRepository.findByEmailIdOrderByStartedAtDesc(emailId);
        return traces.stream().map(this::toResponse).toList();
    }

    private EmailAutomationTraceResponse toResponse(EmailAutomationTrace trace) {
        List<EmailNodeTraceResponse> nodeResponses = trace.getNodeTraces().stream()
                .map(this::toNodeResponse)
                .toList();

        return new EmailAutomationTraceResponse(
                trace.getId(),
                trace.getAutomationId(),
                trace.getAutomationName(),
                trace.getAutomationColor(),
                trace.getStartedAt(),
                trace.getCompletedAt(),
                trace.getStatus().name(),
                trace.isSimulation(),
                nodeResponses
        );
    }

    private EmailNodeTraceResponse toNodeResponse(EmailNodeTrace nt) {
        Object detail = null;
        if (nt.getResultDetail() != null) {
            try {
                detail = objectMapper.readValue(nt.getResultDetail(), Object.class);
            } catch (JsonProcessingException e) {
                detail = nt.getResultDetail();
            }
        }

        return new EmailNodeTraceResponse(
                nt.getId(),
                nt.getNodeId(),
                nt.getNodeType().name(),
                nt.getNodeLabel(),
                nt.getExecutionOrder(),
                nt.getResultStatus().name(),
                detail,
                nt.getExecutedAt()
        );
    }
}
