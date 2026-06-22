package com.postwerk.dto.admin;

import java.time.Instant;
import java.util.UUID;

/** Response DTO for an editable per-model AI pricing row. */
public record ModelPricingResponse(
        UUID id,
        String model,
        double inputPerMillion,
        double outputPerMillion,
        Instant updatedAt
) {}
