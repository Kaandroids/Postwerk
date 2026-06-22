package com.postwerk.dto.automation;

import java.util.List;

/**
 * Result of running the {@link com.postwerk.service.AutomationValidator} against an automation flow.
 *
 * @param valid  {@code true} when there are no {@code error}-severity issues (warnings are allowed)
 * @param issues every detected issue, in deterministic order
 */
public record AutomationValidationResult(boolean valid, List<ValidationIssue> issues) {

    /** Builds a result whose {@code valid} flag is derived from the absence of error issues. */
    public static AutomationValidationResult of(List<ValidationIssue> issues) {
        boolean valid = issues.stream().noneMatch(ValidationIssue::isError);
        return new AutomationValidationResult(valid, issues);
    }

    /** Concatenated messages of all error issues, for exception text. */
    public String errorSummary() {
        return issues.stream()
                .filter(ValidationIssue::isError)
                .map(ValidationIssue::message)
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
    }
}
