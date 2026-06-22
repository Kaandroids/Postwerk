package com.postwerk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request DTO for creating or updating an encrypted secret. */
public record SecretRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        String value
) {}
