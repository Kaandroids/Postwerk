package com.postwerk.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response DTO for an email's automation execution trace with node-level details. */
public record EmailAutomationTraceResponse(
    UUID id,
    UUID automationId,
    String automationName,
    String automationColor,
    Instant startedAt,
    Instant completedAt,
    String status,
    boolean simulation,
    List<EmailNodeTraceResponse> nodeTraces
) {}
