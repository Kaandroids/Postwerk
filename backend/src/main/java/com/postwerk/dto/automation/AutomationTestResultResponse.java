package com.postwerk.dto.automation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response DTO for a detailed automation test execution result. */
public record AutomationTestResultResponse(
        UUID id,
        UUID testCaseId,
        String testCaseName,
        String status,
        List<NodeTraceResult> nodeResults,
        List<AssertionResult> assertionResults,
        long durationMs,
        String errorMessage,
        Instant executedAt
) {}
