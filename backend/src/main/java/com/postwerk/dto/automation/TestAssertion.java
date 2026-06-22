package com.postwerk.dto.automation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** DTO defining an expected outcome assertion for a specific node in a test case. */
public record TestAssertion(
        @NotNull UUID nodeId,
        @NotBlank String expectedStatus,
        String field,
        String expectedValue
) {}
