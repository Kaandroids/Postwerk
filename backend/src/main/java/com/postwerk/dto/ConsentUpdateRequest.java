package com.postwerk.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload for updating a user's marketing consent preference.
 *
 * @param marketingOptIn whether the user opts in to marketing communications
 */
public record ConsentUpdateRequest(
        @NotNull(message = "marketingOptIn is required") Boolean marketingOptIn
) {}
