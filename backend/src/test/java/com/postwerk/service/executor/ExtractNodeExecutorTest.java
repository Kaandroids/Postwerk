package com.postwerk.service.executor;

import com.postwerk.TestFixtures;
import com.postwerk.dto.AiAttachment;
import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.postwerk.model.ParameterSet;
import com.postwerk.repository.ParameterSetRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExtractNodeExecutor}'s attachment handling: the {@code includeAttachments}
 * opt-in fetches the email's attachments via {@link AttachmentContentResolver} and forwards them to
 * {@link GeminiService#extract} as inline AI input; otherwise the extract call carries no attachments.
 */
@ExtendWith(MockitoExtension.class)
class ExtractNodeExecutorTest {

    @Mock private GeminiService geminiService;
    @Mock private ParameterSetRepository parameterSetRepository;
    @Mock private AttachmentContentResolver attachmentResolver;

    private ExtractNodeExecutor executor;
    private final ObjectMapper mapper = new ObjectMapper();
    private Email email;
    private EmailAccount account;
    private ExecutionContext ctx;
    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID paramSetId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        executor = new ExtractNodeExecutor(geminiService, parameterSetRepository, mapper, attachmentResolver);
        account = TestFixtures.createEmailAccount(UUID.randomUUID());
        email = TestFixtures.createEmail(UUID.randomUUID());
        ctx = new ExecutionContext(email, account).withOrganizationId(orgId);

        ParameterSet ps = mock(ParameterSet.class);
        lenient().when(ps.getParameters()).thenReturn("[]");
        lenient().when(parameterSetRepository.findById(paramSetId)).thenReturn(Optional.of(ps));
        lenient().when(geminiService.extract(eq(orgId), eq(userId), anyString(), anyList(), anyList()))
                .thenReturn(Map.of("field", "value"));
    }

    /** Config with the given source variables and one extraction. */
    private JsonNode cfg(String... sourceVars) throws Exception {
        String arr = java.util.Arrays.stream(sourceVars)
                .map(s -> "\"" + s + "\"").collect(java.util.stream.Collectors.joining(","));
        return mapper.readTree("{\"sourceVariables\":[" + arr
                + "],\"extractions\":[{\"parameterSetId\":\"" + paramSetId + "\"}]}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void attachmentsSourceVariable_fetchesAndForwardsThemToGemini() throws Exception {
        when(attachmentResolver.fetch(eq(account), eq(email), any(AttachmentSelection.class)))
                .thenReturn(new AttachmentFetchResult(
                        List.of(new FetchedAttachment(0, "invoice.pdf", "application/pdf", new byte[16])),
                        List.of(new SkippedAttachment(1, "sheet.xlsx",
                                "application/vnd.ms-excel", SkipReason.UNSUPPORTED_TYPE))));

        executor.execute(email, cfg("email.attachments"), userId, ctx);

        ArgumentCaptor<List<AiAttachment>> captor = ArgumentCaptor.forClass(List.class);
        verify(geminiService).extract(eq(orgId), eq(userId), anyString(), anyList(), captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).mimeType()).isEqualTo("application/pdf");
        assertThat(captor.getValue().get(0).filename()).isEqualTo("invoice.pdf");
    }

    @Test
    @SuppressWarnings("unchecked")
    void withoutAttachmentsSource_noFetch_andNoAttachmentsSent() throws Exception {
        executor.execute(email, cfg("email.body"), userId, ctx);

        verify(attachmentResolver, never()).fetch(any(), any(), any());
        ArgumentCaptor<List<AiAttachment>> captor = ArgumentCaptor.forClass(List.class);
        verify(geminiService).extract(eq(orgId), eq(userId), anyString(), anyList(), captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }
}
