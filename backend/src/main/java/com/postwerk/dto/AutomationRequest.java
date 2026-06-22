package com.postwerk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request DTO for creating or updating an automation workflow. */
public record AutomationRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 1000) String description,
        @Size(max = 20) String color,
        @Size(max = 20) String kind
) {}
