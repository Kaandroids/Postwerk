package com.postwerk.dto.automation;

import java.util.List;

/** Response DTO for a batch test run with per-test-case results. */
public record RunAllTestsResponse(
        int totalTests,
        int passed,
        int failed,
        int errors,
        List<AutomationTestResultResponse> results
) {}
