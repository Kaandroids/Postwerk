package com.postwerk.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Summary response DTO for an email in a list view. */
public record EmailListResponse(
    UUID id,
    String messageId,
    String folder,
    String fromAddress,
    String fromPersonal,
    String toAddresses,
    String ccAddresses,
    String subject,
    String snippet,
    Instant receivedAt,
    boolean isRead,
    boolean isStarred,
    boolean hasAttachments,
    String attachments,
    Long sizeBytes,
    List<EmailCategoryItem> categories,
    int automationTraceCount,
    String approvalStatus,
    boolean processed
) {}
