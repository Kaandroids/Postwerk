package com.postwerk.dto.automation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Response DTO for an automation test case with its latest result. */
public record AutomationTestCaseResponse(
        UUID id,
        String name,
        String description,
        TestEmailInput emailInput,
        List<TestAssertion> assertions,
        Map<String, NodeMock> mocks,
        int sortOrder,
        TestResultSummary lastResult,
        Instant createdAt
) {}
