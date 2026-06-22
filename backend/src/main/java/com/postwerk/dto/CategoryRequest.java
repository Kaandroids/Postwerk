package com.postwerk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request DTO for creating or updating a classification category. */
public record CategoryRequest(
        @NotBlank @Size(min = 3, max = 100) String name,
        @NotBlank @Size(max = 20) String color,
        @NotBlank @Size(min = 30, max = 2000) String description,
        @Size(max = 2000) String positiveExample,
        @Size(max = 2000) String negativeExample
) {}
