package com.postwerk.dto;

import java.util.UUID;

/** Response DTO for a draft email attachment metadata. */
public record DraftAttachmentResponse(
    UUID id,
    String fileName,
    String contentType,
    long sizeBytes
) {}
