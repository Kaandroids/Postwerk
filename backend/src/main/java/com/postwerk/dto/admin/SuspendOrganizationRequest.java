package com.postwerk.dto.admin;

import jakarta.validation.constraints.Size;

/** Optional payload for suspending an organization; {@code reason} is recorded for support/audit. */
public record SuspendOrganizationRequest(
        @Size(max = 500) String reason
) {}
