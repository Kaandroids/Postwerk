package com.postwerk.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Detailed response DTO for a single email with full content and metadata. */
public record EmailResponse(
    UUID id,
    String messageId,
    String folder,
    String fromAddress,
    String fromPersonal,
    String toAddresses,
    String ccAddresses,
    String subject,
    String bodyText,
    String bodyHtml,
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
    boolean processed,
    List<EmailAutomationTraceResponse> automationTraces
) {}
