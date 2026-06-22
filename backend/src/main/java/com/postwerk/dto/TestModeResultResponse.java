package com.postwerk.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TestModeResultResponse(
        UUID id,
        UUID emailId,
        String emailSubject,
        String emailFrom,
        Instant emailReceivedAt,
        UUID traceId,
        List<SimulatedAction> simulatedActions,
        String feedback,
        String feedbackNote,
        Instant createdAt,
        Instant feedbackAt
) {}
