package com.postwerk.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response DTO for a parameter set with its parameter definitions. */
public record ParameterSetResponse(
        UUID id,
        String name,
        List<ParameterItemDto> parameters,
        boolean locked,
        Instant createdAt,
        Instant updatedAt
) {}
