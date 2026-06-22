package com.postwerk.dto.automation;

/** Result of a single test assertion comparing expected vs. actual values. */
public record AssertionResult(
        int assertionIndex,
        boolean passed,
        String expected,
        String actual
) {}
