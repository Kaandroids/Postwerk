package com.postwerk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request DTO for updating the authenticated user's profile information. */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String fullName,
        @Size(max = 100) String company,
        @Size(max = 30) String phone
) {}
