package com.postwerk.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * New-flag payload. The {@code key} is immutable after create (dot/kebab-case, lowercase). A new flag
 * starts disabled at 0% / EVERYONE.
 *
 * @since 1.0
 */
public record CreateFlagRequest(
        @NotBlank @Size(max = 120)
        @Pattern(regexp = "^[a-z0-9]+([._-][a-z0-9]+)*$", message = "Key must be lowercase dot/kebab-case")
        String key,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 4000) String description,
        @NotBlank String kind
) {}
