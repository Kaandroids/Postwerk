package com.postwerk.dto.org;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to create a new collaborative organization.
 *
 * @since 1.0
 */
public record CreateOrganizationRequest(
        @NotBlank @Size(max = 120) String name) {
}
