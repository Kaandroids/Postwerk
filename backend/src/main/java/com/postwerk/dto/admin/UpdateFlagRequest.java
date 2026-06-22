package com.postwerk.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Save payload for an existing flag (key is immutable, so not included). Targeting + overrides +
 * rollout + enabled are all set together from the editor.
 *
 * @since 1.0
 */
public record UpdateFlagRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 4000) String description,
        @NotBlank String kind,
        boolean enabled,
        @Min(0) @Max(100) int rollout,
        String audience,
        List<String> audiencePlans,
        UUID audienceOrgId,
        List<FlagOverrideDto> overrides
) {}
