package com.postwerk.dto;

import java.time.Instant;
import java.util.UUID;

/** Response DTO for a single node execution result within a trace. */
public record EmailNodeTraceResponse(
    UUID id,
    UUID nodeId,
    String nodeType,
    String nodeLabel,
    int executionOrder,
    String resultStatus,
    Object resultDetail,
    Instant executedAt
) {}
