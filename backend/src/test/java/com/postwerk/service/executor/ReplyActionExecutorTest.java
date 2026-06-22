package com.postwerk.service.executor;

import com.postwerk.TestFixtures;
import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.postwerk.repository.TemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReplyActionExecutor}. The SMTP send is delegated to {@link MailSendingSupport}
 * (mocked), so these verify the template/content guards and that a reply is composed (with placeholder
 * resolution) and dispatched as HTML to the original sender — without sending.
 */
@ExtendWith(MockitoExtension.class)
class ReplyActionExecutorTest {

    @Mock private TemplateRepository templateRepository;
    @Mock private MailSendingSupport mailSendingSupport;

    private ReplyActionExecutor executor;
    private final ObjectMapper mapper = new ObjectMapper();
    private Email email;
    private EmailAccount account;
    private ExecutionContext ctx;

    @BeforeEach
    void setUp() {
        executor = new ReplyActionExecutor(templateRepository, mailSendingSupport);
        account = TestFixtures.createEmailAccount(UUID.randomUUID());
        email = TestFixtures.createEmail(UUID.randomUUID());
        ctx = new ExecutionContext(email, account);
    }

    private JsonNode cfg(String json) throws Exception {
        return mapper.readTree(json);
    }

    @Test
    void getActionType_isReplyTemplate() {
        assertThat(executor.getActionType()).isEqualTo("REPLY_TEMPLATE");
    }

    @Test
    void templateMode_templateNotFound_throws() throws Exception {
        when(templateRepository.findById(any())).thenReturn(Optional.empty());
        var config = cfg("{\"contentSource\":\"VORLAGE\",\"templateId\":\"" + UUID.randomUUID() + "\"}");

        assertThatThrownBy(() -> executor.execute(email, account, config, ctx))
                .isInstanceOf(IllegalArgumentException.class);

        verify(mailSendingSupport, never()).send(any(), any());
    }

    @Test
    void manualMode_missingContent_throws() throws Exception {
        assertThatThrownBy(() -> executor.execute(email, account, cfg("{\"contentSource\":\"MANUAL\"}"), ctx))
                .isInstanceOf(IllegalArgumentException.class);

        verify(mailSendingSupport, never()).send(any(), any());
    }

    @Test
    void manualMode_repliesAsHtml_toSender_withResolvedPlaceholders() throws Exception {
        var config = cfg("{\"contentSource\":\"MANUAL\",\"subject\":\"Re: {{subject}}\",\"body\":\"Thanks!\"}");

        executor.execute(email, account, config, ctx);

        ArgumentCaptor<OutgoingMail> captor = ArgumentCaptor.forClass(OutgoingMail.class);
        verify(mailSendingSupport).send(any(), captor.capture());
        OutgoingMail mail = captor.getValue();
        assertThat(mail.to()).isEqualTo(email.getFromAddress());
        assertThat(mail.html()).isTrue();
        assertThat(mail.subject()).contains("Test Email Subject"); // {{subject}} resolved from the email
    }
}
