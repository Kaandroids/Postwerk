package com.postwerk.service.executor;

import com.postwerk.dto.AiAttachment;
import com.postwerk.service.executor.AttachmentContentResolver.AttachmentFetchResult;
import com.postwerk.service.executor.AttachmentContentResolver.AttachmentSelection;

import java.util.List;
import java.util.Set;

/**
 * Policy and mapping for feeding email attachments to the Gemini-backed AI nodes (EXTRACT, CATEGORIZE).
 *
 * <p>Gemini reads PDFs (each page is processed with vision), images and plain text/CSV inline; Office
 * formats such as XLSX/DOCX are <b>not</b> natively understood and are therefore excluded. Inline
 * requests are bounded by Gemini's ~20&nbsp;MB total-request limit, so the per-file, total and count
 * caps below keep a single multimodal request inside that budget.</p>
 *
 * @since 1.0
 */
public final class AiAttachmentSupport {

    private AiAttachmentSupport() {
    }

    /**
     * Source-variable key that, when selected on an EXTRACT/CATEGORIZE node, feeds the email's
     * attachments to the AI (instead of being treated as text). This is how users opt in to
     * attachment input — by adding {@code email.attachments} as a source variable.
     */
    public static final String SOURCE_KEY = "email.attachments";

    /**
     * Suffix of the internal variable a FOREACH-over-{@code email.attachments} binds on each element
     * ({@code <alias>.__attachmentIndex}). It lets a downstream AI node fed the loop alias as a source
     * variable fetch just that one attachment's bytes by index.
     */
    public static final String ITEM_INDEX_SUFFIX = ".__attachmentIndex";

    /** Content types Gemini can ingest inline. A trailing {@code /} matches the whole family (e.g. {@code image/}). */
    public static final Set<String> ALLOWED_TYPES = Set.of("application/pdf", "image/", "text/");

    /** Max number of attachments sent in one AI call. */
    public static final int MAX_COUNT = 5;

    /** Per-file size cap (bytes). */
    public static final long MAX_PER_FILE_BYTES = 15L * 1024 * 1024;

    /** Cumulative size cap (bytes) across all attachments in one AI call, under Gemini's inline budget. */
    public static final long MAX_TOTAL_BYTES = 18L * 1024 * 1024;

    /** The attachment selection used when feeding all of an email's attachments to a Gemini AI node. */
    public static AttachmentSelection selection() {
        return new AttachmentSelection(null, ALLOWED_TYPES, MAX_COUNT, MAX_PER_FILE_BYTES, MAX_TOTAL_BYTES);
    }

    /** Selection targeting a single attachment by index (the FOREACH per-item case), same type/size guards. */
    public static AttachmentSelection selectionForIndex(int index) {
        return new AttachmentSelection(Set.of(index), ALLOWED_TYPES, 1, MAX_PER_FILE_BYTES, MAX_TOTAL_BYTES);
    }

    /** Maps the successfully fetched attachments to inline {@link AiAttachment} parts. */
    public static List<AiAttachment> toAiAttachments(AttachmentFetchResult result) {
        return result.fetched().stream()
                .map(f -> new AiAttachment(f.filename(), f.contentType(), f.data()))
                .toList();
    }
}
