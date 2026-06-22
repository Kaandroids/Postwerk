package com.postwerk.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Request DTO for replacing an automation's set of user-defined constants. */
public record ConstantsUpdateRequest(
        @NotNull List<AutomationConstantDto> constants
) {}
