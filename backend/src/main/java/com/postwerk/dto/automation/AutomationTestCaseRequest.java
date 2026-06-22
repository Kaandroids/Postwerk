package com.postwerk.dto.automation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** Request DTO for creating or updating an automation test case. */
public record AutomationTestCaseRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        @NotNull @Valid TestEmailInput emailInput,
        List<@Valid TestAssertion> assertions,
        Map<String, NodeMock> mocks
) {}
