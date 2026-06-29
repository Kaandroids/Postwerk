package com.postwerk.service.executor;

import com.postwerk.service.executor.AttachmentContentResolver.AttachmentFetchResult;
import com.postwerk.service.executor.AttachmentContentResolver.AttachmentSelection;
import com.postwerk.service.executor.AttachmentContentResolver.FetchedAttachment;
import com.postwerk.service.executor.AttachmentContentResolver.SkipReason;
import com.postwerk.service.executor.AttachmentContentResolver.SkippedAttachment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OutgoingAttachmentSupport} — the policy/mapping for re-attaching files to a
 * forward. Unlike the AI path there is no content-type allowlist (you forward whatever arrived), but
 * count/size are still bounded.
 */
class OutgoingAttachmentSupportTest {

    @Test
    void selection_all_hasNoTypeAllowlist_butCountAndSizeCaps() {
        AttachmentSelection sel = OutgoingAttachmentSupport.selection();

        assertThat(sel.indices()).isNull();              // all
        assertThat(sel.allowedContentTypes()).isNull();  // any type may be forwarded
        assertThat(sel.maxCount()).isEqualTo(OutgoingAttachmentSupport.MAX_COUNT);
        assertThat(sel.maxPerFileBytes()).isEqualTo(OutgoingAttachmentSupport.MAX_PER_FILE_BYTES);
        assertThat(sel.maxTotalBytes()).isEqualTo(OutgoingAttachmentSupport.MAX_TOTAL_BYTES);
    }

    @Test
    void selectionForIndex_targetsThatIndex_noTypeAllowlist_countOne() {
        AttachmentSelection sel = OutgoingAttachmentSupport.selectionForIndex(2);

        assertThat(sel.indices()).containsExactly(2);
        assertThat(sel.allowedContentTypes()).isNull();
        assertThat(sel.maxCount()).isEqualTo(1);
    }

    @Test
    void toOutgoing_mapsFetchedOnly_preservingNameTypeAndBytes() {
        byte[] data = {9, 8, 7};
        AttachmentFetchResult result = new AttachmentFetchResult(
                List.of(new FetchedAttachment(0, "report.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", data)),
                List.of(new SkippedAttachment(1, "huge.zip", "application/zip", SkipReason.TOO_LARGE)));

        List<OutgoingAttachment> mapped = OutgoingAttachmentSupport.toOutgoing(result);

        assertThat(mapped).hasSize(1);
        OutgoingAttachment a = mapped.get(0);
        assertThat(a.filename()).isEqualTo("report.docx");
        assertThat(a.contentType()).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(a.data()).isSameAs(data);
    }
}
