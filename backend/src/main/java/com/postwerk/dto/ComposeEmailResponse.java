package com.postwerk.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response DTO returned after sending or saving an email. */
public record ComposeEmailResponse(
    UUID id,
    String messageId,
    String folder,
    String toAddresses,
    String ccAddresses,
    String bccAddresses,
    String subject,
    String bodyHtml,
    Instant sentAt,
    boolean isDraft,
    List<DraftAttachmentResponse> attachments
) {}
