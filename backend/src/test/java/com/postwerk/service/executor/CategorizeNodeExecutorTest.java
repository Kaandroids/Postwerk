package com.postwerk.service.executor;

import com.postwerk.TestFixtures;
import com.postwerk.dto.AiAttachment;
import com.postwerk.dto.ClassificationResult;
import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.postwerk.repository.CategoryRepository;
import com.postwerk.repository.EmailRepository;
import com.postwerk.service.AuditService;
import com.postwerk.service.EmbeddingService;
import com.postwerk.service.GeminiService;
import com.postwerk.service.executor.AttachmentContentResolver.AttachmentFetchResult;
import com.postwerk.service.executor.AttachmentContentResolver.AttachmentSelection;
import com.postwerk.service.executor.AttachmentContentResolver.FetchedAttachment;
import com.postwerk.service.executor.AttachmentContentResolver.SkipReason;
import com.postwerk.service.executor.AttachmentContentResolver.SkippedAttachment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CategorizeNodeExecutor}'s attachment handling: the {@code includeAttachments}
 * opt-in fetches the email's attachments via {@link AttachmentContentResolver} and forwards them to
 * {@link GeminiService#classify} (the text embedding/vector step stays text-only).
 */
@ExtendWith(MockitoExtension.class)
class CategorizeNodeExecutorTest {

    @Mock private EmbeddingService embeddingService;
    @Mock private GeminiService geminiService;
    @Mock private CategoryRepository categoryRepository;
    @Mock private EmailRepository emailRepository;
    @Mock private AuditService auditService;
    @Mock private AttachmentContentResolver attachmentResolver;

    private CategorizeNodeExecutor executor;
    private final ObjectMapper mapper = new ObjectMapper();
    private Email email;
    private EmailAccount account;
    private ExecutionContext ctx;
    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        executor = new CategorizeNodeExecutor(embeddingService, geminiService, categoryRepository,
                emailRepository, auditService, mapper, attachmentResolver);
        account = TestFixtures.createEmailAccount(UUID.randomUUID());
        email = TestFixtures.createEmail(UUID.randomUUID());
        ctx = new ExecutionContext(email, account).withOrganizationId(orgId);

        lenient().when(embeddingService.embed(eq(orgId), eq(userId), anyString())).thenReturn(new float[]{1f});
        lenient().when(categoryRepository.findClosestByEmbedding(anyList(), anyString(), eq(5)))
                .thenReturn(List.<Object[]>of(new Object[]{categoryId, "Invoices", "dist", "desc", "pos", "neg"}));
        lenient().when(categoryRepository.findById(any())).thenReturn(Optional.empty());
        lenient().when(geminiService.classify(eq(orgId), eq(userId), anyString(), anyList(), anyList()))
                .thenReturn(new ClassificationResult(categoryId.toString(), 90, "matches"));
    }

    /** Config with the given source variables, one category and a threshold. */
    private JsonNode cfg(String... sourceVars) throws Exception {
        String arr = java.util.Arrays.stream(sourceVars)
                .map(s -> "\"" + s + "\"").collect(java.util.stream.Collectors.joining(","));
        return mapper.readTree("{\"sourceVariables\":[" + arr
                + "],\"categoryIds\":[\"" + categoryId + "\"],\"threshold\":70}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void attachmentsSourceVariable_fetchesAndForwardsThemToClassify() throws Exception {
        when(attachmentResolver.fetch(eq(account), eq(email), any(AttachmentSelection.class)))
                .thenReturn(new AttachmentFetchResult(
                        List.of(new FetchedAttachment(0, "scan.png", "image/png", new byte[8])),
                        List.of(new SkippedAttachment(1, "big.pdf",
                                "application/pdf", SkipReason.TOO_LARGE))));

        executor.executeDetailed(email, cfg("email.body", "email.attachments"), userId, false, ctx);

        ArgumentCaptor<List<AiAttachment>> captor = ArgumentCaptor.forClass(List.class);
        verify(geminiService).classify(eq(orgId), eq(userId), anyString(), anyList(), captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).mimeType()).isEqualTo("image/png");
    }

    @Test
    @SuppressWarnings("unchecked")
    void withoutAttachmentsSource_noFetch_andNoAttachmentsSent() throws Exception {
        executor.executeDetailed(email, cfg("email.body"), userId, false, ctx);

        verify(attachmentResolver, never()).fetch(any(), any(), any());
        ArgumentCaptor<List<AiAttachment>> captor = ArgumentCaptor.forClass(List.class);
        verify(geminiService).classify(eq(orgId), eq(userId), anyString(), anyList(), captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }
}
