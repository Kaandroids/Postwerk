package com.postwerk.service.executor;

import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.postwerk.service.EmailSyncService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shared, on-demand resolver for an incoming email's attachment <em>content</em> (bytes).
 *
 * <p>Attachment binaries are never persisted — only the metadata JSON ({@code Email.attachments})
 * and a {@code has_attachments} flag are stored. This component is the single place that turns that
 * metadata into actual bytes by fetching them from IMAP via
 * {@link EmailSyncService#downloadAttachment(EmailAccount, long, int, String)}, so both upcoming
 * consumers — feeding attachments to the AI nodes and re-attaching them to outgoing mail — share one
 * fetch path, one selection model and one set of guardrails.</p>
 *
 * <p><b>Index parity.</b> The {@code index} of an {@link AttachmentRef} is its position in the
 * persisted {@code attachments} JSON array, which is produced by
 * {@code MimeMessageParser.collectAttachmentsFromMultipart}. {@code downloadAttachment} addresses the
 * same part by the position produced by {@code MimeMessageParser.collectAttachmentParts}. Both walk
 * the MIME tree with the identical predicate and recursion, so position {@code i} refers to the same
 * body part in both — this resolver relies on that invariant.</p>
 *
 * <p>All methods are stateless and side-effect free apart from the IMAP read.</p>
 *
 * @since 1.0
 */
@Component
public class AttachmentContentResolver {

    private static final Logger log = LoggerFactory.getLogger(AttachmentContentResolver.class);

    private final EmailSyncService emailSyncService;
    private final ObjectMapper objectMapper;

    public AttachmentContentResolver(EmailSyncService emailSyncService, ObjectMapper objectMapper) {
        this.emailSyncService = emailSyncService;
        this.objectMapper = objectMapper;
    }

    /** Lightweight attachment metadata, parsed from {@code Email.attachments}. */
    public record AttachmentRef(int index, String filename, String contentType, String sizeLabel) {}

    /** A downloaded attachment together with its raw bytes. */
    public record FetchedAttachment(int index, String filename, String contentType, byte[] data) {
        public long size() {
            return data != null ? data.length : 0;
        }
    }

    /** Why a selected attachment was excluded from a fetch result. */
    public enum SkipReason {
        NO_UID, UNSUPPORTED_TYPE, COUNT_EXCEEDED, DOWNLOAD_FAILED, TOO_LARGE, TOTAL_SIZE_EXCEEDED
    }

    /** A selected attachment that was not fetched, with the reason for downstream reporting. */
    public record SkippedAttachment(int index, String filename, String contentType, SkipReason reason) {}

    /** Outcome of a {@link #fetch} call: the attachments that were downloaded plus those that were skipped. */
    public record AttachmentFetchResult(List<FetchedAttachment> fetched, List<SkippedAttachment> skipped) {
        public boolean isEmpty() {
            return fetched.isEmpty();
        }
    }

    /**
     * Selection criteria and guardrails for {@link #fetch}.
     *
     * @param indices             positions to include; {@code null} means all attachments
     * @param allowedContentTypes case-insensitive allowlist; {@code null}/empty allows any type. An
     *                            entry ending in {@code /} matches by prefix (e.g. {@code image/}),
     *                            otherwise it must match the bare content type exactly
     * @param maxCount            maximum number of attachments to include
     * @param maxPerFileBytes     per-file size cap (enforced on the downloaded bytes)
     * @param maxTotalBytes       cumulative size cap across all included attachments
     */
    public record AttachmentSelection(
            Set<Integer> indices,
            Set<String> allowedContentTypes,
            int maxCount,
            long maxPerFileBytes,
            long maxTotalBytes
    ) {
        /** Select every attachment, applying only the given count/size guardrails (no type restriction). */
        public static AttachmentSelection all(int maxCount, long maxPerFileBytes, long maxTotalBytes) {
            return new AttachmentSelection(null, null, maxCount, maxPerFileBytes, maxTotalBytes);
        }
    }

    /**
     * Parses the email's persisted attachment metadata into typed refs. Never throws: returns an empty
     * list when there are no attachments or the JSON cannot be parsed.
     */
    public List<AttachmentRef> list(Email email) {
        String json = email != null ? email.getAttachments() : null;
        if (json == null || json.isBlank() || json.equals("[]")) {
            return List.of();
        }
        List<Map<String, Object>> raw;
        try {
            raw = objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse attachments JSON for email {}: {}",
                    email.getId(), e.getMessage());
            return List.of();
        }
        List<AttachmentRef> refs = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            Map<String, Object> entry = raw.get(i);
            refs.add(new AttachmentRef(
                    i,
                    str(entry.get("name")),
                    str(entry.get("contentType")),
                    str(entry.get("size"))));
        }
        return refs;
    }

    /**
     * Downloads a single attachment's bytes by index, or {@code null} when it cannot be fetched
     * (missing UID, IMAP error). Errors are logged, not thrown, so callers can fall through gracefully.
     */
    public FetchedAttachment fetchByIndex(EmailAccount account, Email email, int index) {
        if (email == null || email.getUid() == null) {
            return null;
        }
        try {
            Object[] r = emailSyncService.downloadAttachment(account, email.getUid(), index, email.getFolder());
            return new FetchedAttachment(index, (String) r[0], (String) r[1], (byte[]) r[2]);
        } catch (Exception e) {
            log.warn("Failed to fetch attachment {} of email {}: {}", index, email.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * Selects attachments per {@code selection} and downloads their bytes, enforcing the type, count
     * and size guardrails. Excluded attachments are reported in {@link AttachmentFetchResult#skipped}
     * with a {@link SkipReason} rather than aborting the whole batch, so one bad/oversized attachment
     * never sinks the others.
     */
    public AttachmentFetchResult fetch(EmailAccount account, Email email, AttachmentSelection selection) {
        List<FetchedAttachment> fetched = new ArrayList<>();
        List<SkippedAttachment> skipped = new ArrayList<>();

        List<AttachmentRef> refs = list(email);
        boolean haveUid = email != null && email.getUid() != null;
        long total = 0;

        for (AttachmentRef ref : refs) {
            if (selection.indices() != null && !selection.indices().contains(ref.index())) {
                continue; // not selected — silently ignored (not a skip)
            }
            if (!haveUid) {
                skipped.add(skip(ref, SkipReason.NO_UID));
                continue;
            }
            if (!typeAllowed(ref.contentType(), selection.allowedContentTypes())) {
                skipped.add(skip(ref, SkipReason.UNSUPPORTED_TYPE));
                continue;
            }
            if (fetched.size() >= selection.maxCount()) {
                skipped.add(skip(ref, SkipReason.COUNT_EXCEEDED));
                continue;
            }

            FetchedAttachment att = fetchByIndex(account, email, ref.index());
            if (att == null) {
                skipped.add(skip(ref, SkipReason.DOWNLOAD_FAILED));
                continue;
            }
            if (att.size() > selection.maxPerFileBytes()) {
                skipped.add(skip(ref, SkipReason.TOO_LARGE));
                continue;
            }
            if (total + att.size() > selection.maxTotalBytes()) {
                skipped.add(skip(ref, SkipReason.TOTAL_SIZE_EXCEEDED));
                continue;
            }

            fetched.add(att);
            total += att.size();
        }

        return new AttachmentFetchResult(fetched, skipped);
    }

    // ─── internals ────────────────────────────────────────────────

    private static SkippedAttachment skip(AttachmentRef ref, SkipReason reason) {
        return new SkippedAttachment(ref.index(), ref.filename(), ref.contentType(), reason);
    }

    /** True when no allowlist is given, or {@code contentType} matches an exact entry or a {@code type/} prefix. */
    private static boolean typeAllowed(String contentType, Set<String> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        if (contentType == null) {
            return false;
        }
        String ct = contentType.toLowerCase(Locale.ROOT).trim();
        for (String a : allowed) {
            String entry = a.toLowerCase(Locale.ROOT).trim();
            if (entry.endsWith("/") ? ct.startsWith(entry) : ct.equals(entry)) {
                return true;
            }
        }
        return false;
    }

    private static String str(Object o) {
        return o != null ? o.toString() : null;
    }
}
