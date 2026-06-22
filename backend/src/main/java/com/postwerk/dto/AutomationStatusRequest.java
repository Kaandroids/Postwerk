package com.postwerk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Request DTO for updating an automation's active/paused status. */
public record AutomationStatusRequest(
        @NotBlank @Pattern(regexp = "ACTIVE|TESTING|PAUSED") String status
) {}
