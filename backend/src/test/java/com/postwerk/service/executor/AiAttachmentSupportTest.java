package com.postwerk.service.executor;

import com.postwerk.dto.AiAttachment;
import com.postwerk.service.executor.AttachmentContentResolver.AttachmentFetchResult;
import com.postwerk.service.executor.AttachmentContentResolver.AttachmentSelection;
import com.postwerk.service.executor.AttachmentContentResolver.FetchedAttachment;
import com.postwerk.service.executor.AttachmentContentResolver.SkipReason;
import com.postwerk.service.executor.AttachmentContentResolver.SkippedAttachment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AiAttachmentSupport} — the policy/mapping for feeding attachments to Gemini:
 * the all-attachments and single-index selections, the type allowlist, and the FetchedAttachment →
 * AiAttachment mapping.
 */
class AiAttachmentSupportTest {

    @Test
    void sourceKeyAndIndexSuffix_areStable() {
        assertThat(AiAttachmentSupport.SOURCE_KEY).isEqualTo("email.attachments");
        assertThat(AiAttachmentSupport.ITEM_INDEX_SUFFIX).isEqualTo(".__attachmentIndex");
    }

    @Test
    void selection_allAttachments_hasAllowlistAndCapsButNoIndexFilter() {
        AttachmentSelection sel = AiAttachmentSupport.selection();

        assertThat(sel.indices()).isNull(); // all
        assertThat(sel.allowedContentTypes()).containsExactlyInAnyOrder("application/pdf", "image/", "text/");
        assertThat(sel.maxCount()).isEqualTo(AiAttachmentSupport.MAX_COUNT);
        assertThat(sel.maxPerFileBytes()).isEqualTo(AiAttachmentSupport.MAX_PER_FILE_BYTES);
        assertThat(sel.maxTotalBytes()).isEqualTo(AiAttachmentSupport.MAX_TOTAL_BYTES);
    }

    @Test
    void selectionForIndex_targetsThatIndex_withAllowlistAndCountOne() {
        AttachmentSelection sel = AiAttachmentSupport.selectionForIndex(3);

        assertThat(sel.indices()).containsExactly(3);
        assertThat(sel.allowedContentTypes()).isEqualTo(AiAttachmentSupport.ALLOWED_TYPES);
        assertThat(sel.maxCount()).isEqualTo(1);
        assertThat(sel.maxPerFileBytes()).isEqualTo(AiAttachmentSupport.MAX_PER_FILE_BYTES);
    }

    @Test
    void toAiAttachments_mapsFetchedOnly_preservingNameTypeAndBytes() {
        byte[] data = {1, 2, 3};
        AttachmentFetchResult result = new AttachmentFetchResult(
                List.of(new FetchedAttachment(0, "invoice.pdf", "application/pdf", data)),
                List.of(new SkippedAttachment(1, "big.pdf", "application/pdf", SkipReason.TOO_LARGE)));

        List<AiAttachment> mapped = AiAttachmentSupport.toAiAttachments(result);

        assertThat(mapped).hasSize(1); // skipped entries are not mapped
        AiAttachment a = mapped.get(0);
        assertThat(a.filename()).isEqualTo("invoice.pdf");
        assertThat(a.mimeType()).isEqualTo("application/pdf");
        assertThat(a.data()).isSameAs(data);
    }

    @Test
    void toAiAttachments_emptyResult_isEmpty() {
        AttachmentFetchResult result = new AttachmentFetchResult(List.of(), List.of());
        assertThat(AiAttachmentSupport.toAiAttachments(result)).isEmpty();
    }
}
