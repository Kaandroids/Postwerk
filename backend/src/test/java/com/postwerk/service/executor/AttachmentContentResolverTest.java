package com.postwerk.service.executor;

import com.postwerk.TestFixtures;
import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.postwerk.service.EmailSyncService;
import com.postwerk.service.executor.AttachmentContentResolver.AttachmentFetchResult;
import com.postwerk.service.executor.AttachmentContentResolver.AttachmentRef;
import com.postwerk.service.executor.AttachmentContentResolver.AttachmentSelection;
import com.postwerk.service.executor.AttachmentContentResolver.SkipReason;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AttachmentContentResolver}. The IMAP download is delegated to
 * {@link EmailSyncService} (mocked), so these verify metadata parsing, the selection model and the
 * type/count/size guardrails — without any real IMAP connection.
 */
@ExtendWith(MockitoExtension.class)
class AttachmentContentResolverTest {

    @Mock private EmailSyncService emailSyncService;

    private AttachmentContentResolver resolver;
    private final ObjectMapper mapper = new ObjectMapper();
    private EmailAccount account;
    private Email email;

    private static final String THREE_ATTACHMENTS = """
            [{"name":"invoice.pdf","size":"120 KB","contentType":"application/pdf"},\
            {"name":"photo.png","size":"2 KB","contentType":"image/png"},\
            {"name":"archive.zip","size":"3 KB","contentType":"application/zip"}]""";

    @BeforeEach
    void setUp() {
        resolver = new AttachmentContentResolver(emailSyncService, mapper);
        account = TestFixtures.createEmailAccount(UUID.randomUUID());
        email = TestFixtures.createEmail(UUID.randomUUID()); // uid=12345, folder=INBOX
        email.setHasAttachments(true);
        email.setAttachments(THREE_ATTACHMENTS);
    }

    private void stubDownload(int index, String name, String contentType, int sizeBytes) {
        when(emailSyncService.downloadAttachment(eq(account), eq(12345L), eq(index), eq("INBOX")))
                .thenReturn(new Object[]{name, contentType, new byte[sizeBytes]});
    }

    // ── list() ────────────────────────────────────────────────────

    @Test
    void list_parsesMetadataWithIndexParity() {
        var refs = resolver.list(email);

        assertThat(refs).extracting(AttachmentRef::index, AttachmentRef::filename, AttachmentRef::contentType)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(0, "invoice.pdf", "application/pdf"),
                        org.assertj.core.groups.Tuple.tuple(1, "photo.png", "image/png"),
                        org.assertj.core.groups.Tuple.tuple(2, "archive.zip", "application/zip"));
    }

    @Test
    void list_returnsEmptyForNoAttachments() {
        email.setAttachments("[]");
        assertThat(resolver.list(email)).isEmpty();
        email.setAttachments(null);
        assertThat(resolver.list(email)).isEmpty();
    }

    @Test
    void list_returnsEmptyOnMalformedJson() {
        email.setAttachments("{not-json");
        assertThat(resolver.list(email)).isEmpty();
    }

    // ── fetch() happy path ────────────────────────────────────────

    @Test
    void fetch_all_downloadsEveryAttachmentInOrder() {
        stubDownload(0, "invoice.pdf", "application/pdf", 100);
        stubDownload(1, "photo.png", "image/png", 50);
        stubDownload(2, "archive.zip", "application/zip", 30);

        AttachmentFetchResult result = resolver.fetch(account, email,
                AttachmentSelection.all(10, 1_000_000, 1_000_000));

        assertThat(result.fetched()).extracting(a -> a.index()).containsExactly(0, 1, 2);
        assertThat(result.skipped()).isEmpty();
    }

    // ── guardrails ────────────────────────────────────────────────

    @Test
    void fetch_typeAllowlist_skipsUnsupportedWithoutDownloading() {
        stubDownload(0, "invoice.pdf", "application/pdf", 100);
        stubDownload(1, "photo.png", "image/png", 50);

        // Allow PDFs and any image/* but not application/zip.
        AttachmentFetchResult result = resolver.fetch(account, email,
                new AttachmentSelection(null, Set.of("application/pdf", "image/"), 10, 1_000_000, 1_000_000));

        assertThat(result.fetched()).extracting(a -> a.index()).containsExactly(0, 1);
        assertThat(result.skipped()).singleElement()
                .satisfies(s -> {
                    assertThat(s.index()).isEqualTo(2);
                    assertThat(s.reason()).isEqualTo(SkipReason.UNSUPPORTED_TYPE);
                });
        // The disallowed zip must never hit IMAP.
        verify(emailSyncService, never()).downloadAttachment(eq(account), eq(12345L), eq(2), eq("INBOX"));
    }

    @Test
    void fetch_perFileSizeCap_skipsOversized() {
        stubDownload(0, "invoice.pdf", "application/pdf", 5_000);
        stubDownload(1, "photo.png", "image/png", 50);
        stubDownload(2, "archive.zip", "application/zip", 30);

        AttachmentFetchResult result = resolver.fetch(account, email,
                AttachmentSelection.all(10, 1_000 /* per-file cap */, 1_000_000));

        assertThat(result.fetched()).extracting(a -> a.index()).containsExactly(1, 2);
        assertThat(result.skipped()).singleElement()
                .satisfies(s -> {
                    assertThat(s.index()).isEqualTo(0);
                    assertThat(s.reason()).isEqualTo(SkipReason.TOO_LARGE);
                });
    }

    @Test
    void fetch_totalSizeCap_skipsOnceBudgetExhausted() {
        stubDownload(0, "invoice.pdf", "application/pdf", 600);
        stubDownload(1, "photo.png", "image/png", 600);
        stubDownload(2, "archive.zip", "application/zip", 600);

        AttachmentFetchResult result = resolver.fetch(account, email,
                AttachmentSelection.all(10, 1_000_000, 1_000 /* total cap */));

        // 0 fits (600), 1 would push total to 1200 > 1000 → skipped, 2 likewise.
        assertThat(result.fetched()).extracting(a -> a.index()).containsExactly(0);
        assertThat(result.skipped()).extracting(s -> s.reason())
                .containsExactly(SkipReason.TOTAL_SIZE_EXCEEDED, SkipReason.TOTAL_SIZE_EXCEEDED);
    }

    @Test
    void fetch_countCap_skipsBeyondLimit() {
        stubDownload(0, "invoice.pdf", "application/pdf", 100);
        stubDownload(1, "photo.png", "image/png", 100);

        AttachmentFetchResult result = resolver.fetch(account, email,
                AttachmentSelection.all(2 /* count cap */, 1_000_000, 1_000_000));

        assertThat(result.fetched()).extracting(a -> a.index()).containsExactly(0, 1);
        assertThat(result.skipped()).singleElement()
                .satisfies(s -> assertThat(s.reason()).isEqualTo(SkipReason.COUNT_EXCEEDED));
        verify(emailSyncService, never()).downloadAttachment(eq(account), eq(12345L), eq(2), eq("INBOX"));
    }

    @Test
    void fetch_indicesSelection_onlyFetchesChosen() {
        stubDownload(2, "archive.zip", "application/zip", 30);

        AttachmentFetchResult result = resolver.fetch(account, email,
                new AttachmentSelection(Set.of(2), null, 10, 1_000_000, 1_000_000));

        assertThat(result.fetched()).extracting(a -> a.index()).containsExactly(2);
        assertThat(result.skipped()).isEmpty();
        verify(emailSyncService, never()).downloadAttachment(eq(account), eq(12345L), eq(0), eq("INBOX"));
    }

    @Test
    void fetch_missingUid_skipsAllWithNoUid() {
        email.setUid(null);

        AttachmentFetchResult result = resolver.fetch(account, email,
                AttachmentSelection.all(10, 1_000_000, 1_000_000));

        assertThat(result.fetched()).isEmpty();
        assertThat(result.skipped()).extracting(s -> s.reason())
                .containsOnly(SkipReason.NO_UID);
        verify(emailSyncService, never()).downloadAttachment(eq(account), anyInt(), anyInt(), eq("INBOX"));
    }

    @Test
    void fetch_downloadFailure_skipsThatOneOnly() {
        stubDownload(0, "invoice.pdf", "application/pdf", 100);
        when(emailSyncService.downloadAttachment(eq(account), eq(12345L), eq(1), eq("INBOX")))
                .thenThrow(new RuntimeException("IMAP boom"));
        stubDownload(2, "archive.zip", "application/zip", 30);

        AttachmentFetchResult result = resolver.fetch(account, email,
                AttachmentSelection.all(10, 1_000_000, 1_000_000));

        assertThat(result.fetched()).extracting(a -> a.index()).containsExactly(0, 2);
        assertThat(result.skipped()).singleElement()
                .satisfies(s -> {
                    assertThat(s.index()).isEqualTo(1);
                    assertThat(s.reason()).isEqualTo(SkipReason.DOWNLOAD_FAILED);
                });
    }

    // ── fetchByIndex() ────────────────────────────────────────────

    @Test
    void fetchByIndex_returnsTypedBytes() {
        stubDownload(1, "photo.png", "image/png", 64);

        var att = resolver.fetchByIndex(account, email, 1);

        assertThat(att).isNotNull();
        assertThat(att.filename()).isEqualTo("photo.png");
        assertThat(att.contentType()).isEqualTo("image/png");
        assertThat(att.size()).isEqualTo(64);
    }

    @Test
    void fetchByIndex_nullWhenNoUid() {
        email.setUid(null);
        assertThat(resolver.fetchByIndex(account, email, 0)).isNull();
    }
}
