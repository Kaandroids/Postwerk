package com.postwerk.service.executor;

import com.postwerk.TestFixtures;
import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.postwerk.repository.TemplateRepository;
import com.postwerk.service.executor.AttachmentContentResolver.AttachmentFetchResult;
import com.postwerk.service.executor.AttachmentContentResolver.AttachmentSelection;
import com.postwerk.service.executor.AttachmentContentResolver.FetchedAttachment;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ForwardActionExecutor}. The real SMTP send is delegated to
 * {@link MailSendingSupport} (mocked), so these verify the recipient guard and that a forward is
 * composed with the resolved note + dispatched with the resolved recipient/subject — without sending.
 */
@ExtendWith(MockitoExtension.class)
class ForwardActionExecutorTest {

    @Mock private MailSendingSupport mailSendingSupport;
    @Mock private TemplateRepository templateRepository;
    @Mock private VariableResolver variableResolver;
    @Mock private AttachmentContentResolver attachmentResolver;

    private ForwardActionExecutor executor;
    private final ObjectMapper mapper = new ObjectMapper();
    private Email email;
    private EmailAccount account;
    private ExecutionContext ctx;

    @BeforeEach
    void setUp() {
        executor = new ForwardActionExecutor(mailSendingSupport, templateRepository, variableResolver,
                attachmentResolver);
        account = TestFixtures.createEmailAccount(UUID.randomUUID());
        email = TestFixtures.createEmail(UUID.randomUUID());
        ctx = new ExecutionContext(email, account);
    }

    private JsonNode cfg(String json) throws Exception {
        return mapper.readTree(json);
    }

    @Test
    void getActionType_isForward() {
        assertThat(executor.getActionType()).isEqualTo("FORWARD");
    }

    @Test
    void missingToAddress_throws_andSendsNothing() throws Exception {
        assertThatThrownBy(() -> executor.execute(email, account, cfg("{}"), ctx))
                .isInstanceOf(IllegalArgumentException.class);

        verify(mailSendingSupport, never()).send(any(), any());
    }

    @Test
    void forwards_withResolvedNote_toResolvedRecipient() throws Exception {
        when(variableResolver.resolve(anyString(), any())).thenAnswer(i -> i.getArgument(0));
        var config = cfg("{\"toAddress\":\"team@x.com\",\"contentSource\":\"MANUAL\","
                + "\"subject\":\"Note subj\",\"body\":\"Note body\"}");

        executor.execute(email, account, config, ctx);

        ArgumentCaptor<OutgoingMail> captor = ArgumentCaptor.forClass(OutgoingMail.class);
        verify(mailSendingSupport).send(eq(account), captor.capture());
        assertThat(captor.getValue().to()).isEqualTo("team@x.com");
        assertThat(captor.getValue().subject()).isEqualTo("Note subj");
        assertThat(captor.getValue().html()).isFalse(); // forward is plain text
        assertThat(captor.getValue().attachments()).isEmpty(); // no opt-in -> no attachments
        verify(attachmentResolver, never()).fetch(any(), any(), any());
    }

    @Test
    void includeAttachments_reAttachesOriginalFiles() throws Exception {
        email.setHasAttachments(true);
        when(attachmentResolver.fetch(eq(account), eq(email), any(AttachmentSelection.class)))
                .thenReturn(new AttachmentFetchResult(
                        List.of(new FetchedAttachment(0, "invoice.pdf", "application/pdf", new byte[12])),
                        List.of()));
        var config = cfg("{\"toAddress\":\"datev@x.com\",\"includeAttachments\":true}");

        executor.execute(email, account, config, ctx);

        ArgumentCaptor<OutgoingMail> captor = ArgumentCaptor.forClass(OutgoingMail.class);
        verify(mailSendingSupport).send(eq(account), captor.capture());
        assertThat(captor.getValue().attachments()).hasSize(1);
        assertThat(captor.getValue().attachments().get(0).filename()).isEqualTo("invoice.pdf");
    }

    @Test
    void includeAttachments_butNoAttachmentsOnEmail_doesNotFetch() throws Exception {
        email.setHasAttachments(false);
        var config = cfg("{\"toAddress\":\"datev@x.com\",\"includeAttachments\":true}");

        executor.execute(email, account, config, ctx);

        verify(attachmentResolver, never()).fetch(any(), any(), any());
        ArgumentCaptor<OutgoingMail> captor = ArgumentCaptor.forClass(OutgoingMail.class);
        verify(mailSendingSupport).send(eq(account), captor.capture());
        assertThat(captor.getValue().attachments()).isEmpty();
    }

    @Test
    void attachmentSourceAll_reAttachesOriginalFiles() throws Exception {
        email.setHasAttachments(true);
        when(attachmentResolver.fetch(eq(account), eq(email), any(AttachmentSelection.class)))
                .thenReturn(new AttachmentFetchResult(
                        List.of(new FetchedAttachment(0, "invoice.pdf", "application/pdf", new byte[12])),
                        List.of()));
        var config = cfg("{\"toAddress\":\"datev@x.com\",\"attachmentSource\":\"email.attachments\"}");

        executor.execute(email, account, config, ctx);

        ArgumentCaptor<OutgoingMail> captor = ArgumentCaptor.forClass(OutgoingMail.class);
        verify(mailSendingSupport).send(eq(account), captor.capture());
        assertThat(captor.getValue().attachments()).extracting(a -> a.filename()).containsExactly("invoice.pdf");
    }

    @Test
    void attachmentSource_foreachItem_attachesOnlyTheCurrentOneByIndex() throws Exception {
        email.setHasAttachments(true);
        ExecutionContext itemCtx = ctx.withVariables(Map.of("item" + AiAttachmentSupport.ITEM_INDEX_SUFFIX, 1));
        ArgumentCaptor<AttachmentSelection> selCaptor = ArgumentCaptor.forClass(AttachmentSelection.class);
        when(attachmentResolver.fetch(eq(account), eq(email), selCaptor.capture()))
                .thenReturn(new AttachmentFetchResult(
                        List.of(new FetchedAttachment(1, "p2.pdf", "application/pdf", new byte[8])), List.of()));
        var config = cfg("{\"toAddress\":\"datev@x.com\",\"attachmentSource\":\"item\"}");

        executor.execute(email, account, config, itemCtx);

        assertThat(selCaptor.getValue().indices()).containsExactly(1);
        ArgumentCaptor<OutgoingMail> captor = ArgumentCaptor.forClass(OutgoingMail.class);
        verify(mailSendingSupport).send(eq(account), captor.capture());
        assertThat(captor.getValue().attachments()).extracting(a -> a.filename()).containsExactly("p2.pdf");
    }
}
