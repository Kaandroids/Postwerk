package com.postwerk.service.executor;

import com.postwerk.service.executor.AttachmentContentResolver.AttachmentFetchResult;
import com.postwerk.service.executor.AttachmentContentResolver.AttachmentSelection;

import java.util.List;

/**
 * Policy and mapping for re-attaching an incoming email's files to an outgoing message
 * (e.g. forwarding the original attachments). Unlike the AI path there is no content-type
 * allowlist — whatever arrived may be forwarded — but size and count are still bounded to keep
 * the message under typical SMTP limits.
 *
 * @since 1.0
 */
public final class OutgoingAttachmentSupport {

    private OutgoingAttachmentSupport() {
    }

    /** Max number of attachments carried on one outgoing message. */
    public static final int MAX_COUNT = 25;

    /** Per-file size cap (bytes). */
    public static final long MAX_PER_FILE_BYTES = 25L * 1024 * 1024;

    /** Cumulative size cap (bytes) across all attachments on one message (typical SMTP limit). */
    public static final long MAX_TOTAL_BYTES = 25L * 1024 * 1024;

    /** Selects every attachment (any type) within the count/size budget for forwarding. */
    public static AttachmentSelection selection() {
        return AttachmentSelection.all(MAX_COUNT, MAX_PER_FILE_BYTES, MAX_TOTAL_BYTES);
    }

    /** Maps the successfully fetched attachments to {@link OutgoingAttachment} MIME parts. */
    public static List<OutgoingAttachment> toOutgoing(AttachmentFetchResult result) {
        return result.fetched().stream()
                .map(f -> new OutgoingAttachment(f.filename(), f.contentType(), f.data()))
                .toList();
    }
}
