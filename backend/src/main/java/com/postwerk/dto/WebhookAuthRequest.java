package com.postwerk.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request to configure inbound webhook authentication.
 *
 * @param authMode       one of NONE, API_KEY, HMAC
 * @param authHeaderName header name for API_KEY mode (nullable, default X-API-Key)
 * @param signingSecret  user-supplied secret (nullable; omit to keep the existing one)
 */
public record WebhookAuthRequest(
        @NotNull @Pattern(regexp = "NONE|API_KEY|HMAC") String authMode,
        @Size(max = 64) String authHeaderName,
        @Size(max = 200) String signingSecret
) {}
