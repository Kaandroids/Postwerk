package com.postwerk.dto.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * A data-subject access request (DSAR) row for the admin Compliance console. Deadline state is
 * derived client-side from {@code deadlineAt} vs now.
 *
 * @since 1.0
 */
public record AdminDataRequestResponse(
        UUID id,
        String subjectName,
        String subjectEmail,
        String org,
        String type,
        String status,
        String channel,
        Instant requestedAt,
        Instant deadlineAt,
        Instant closedAt,
        String handlerName,
        String note,
        String rejectReason
) {}
