package com.postwerk.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Request body for creating/updating a quota override (admin "Quota Overrides").
 *
 * <p>{@code kind} is {@code CREDIT}/{@code CAP}/{@code UNLIMITED}; {@code targetType} is
 * {@code USER}/{@code ORG}. {@code amountCents} is required ({@code > 0}) for CREDIT/CAP and ignored
 * for UNLIMITED — enforced in the service ({@code IllegalArgumentException} → 400). On update the
 * target is locked (a changed target is ignored; the stored target/org is kept).</p>
 *
 * @since 1.0
 */
public record QuotaOverrideRequest(
        @NotBlank String targetType,
        @NotNull UUID targetId,
        @NotBlank String kind,
        Long amountCents,
        Instant expiresAt,
        @NotBlank @Size(max = 1000) String reason
) {}
