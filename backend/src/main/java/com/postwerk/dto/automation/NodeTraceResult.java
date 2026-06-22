package com.postwerk.dto.automation;

import java.util.Map;
import java.util.UUID;

/** DTO for a single node's execution result within a test run trace. */
public record NodeTraceResult(
        UUID nodeId,
        String nodeType,
        String nodeLabel,
        String resultStatus,
        Map<String, Object> resultDetail
) {}
