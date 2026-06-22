package com.postwerk.service.executor;

import com.postwerk.model.AutomationNode;
import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.postwerk.model.Template;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.model.enums.NodeType;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.TemplateRepository;
import com.postwerk.service.EmailSyncService;
import com.postwerk.service.MailConnectionFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static com.postwerk.TestFixtures.createEmail;
import static com.postwerk.TestFixtures.createEmailAccount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SendEmailNodeProcessorTest {

    @Mock private MailConnectionFactory mailConnectionFactory;
    @Mock private EmailSyncService emailSyncService;
    @Mock private EmailAccountRepository emailAccountRepository;
    @Mock private TemplateRepository templateRepository;

    private SendEmailNodeProcessor processor;
    private ObjectMapper objectMapper;
    private UUID userId;
    private EmailAccount account;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        MailSendingSupport mailSendingSupport = new MailSendingSupport(mailConnectionFactory, emailSyncService);
        processor = new SendEmailNodeProcessor(mailSendingSupport,
                emailAccountRepository, templateRepository, objectMapper, new VariableResolver());
        userId = UUID.randomUUID();
        account = createEmailAccount(userId);
    }

    @Test
    void getNodeType_returnsSendEmail() {
        assertThat(processor.getNodeType()).isEqualTo(NodeType.SEND_EMAIL);
    }

    @Test
    void process_dryRun_returnsSimulated() {
        AutomationNode node = buildNode("""
                {"to":"test@example.com","cc":"","bcc":"","subject":"Hello","body":"<p>Hi</p>"}
                """);
        ExecutionContext context = new ExecutionContext(null, account, true);

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.SIMULATED);
        assertThat(result.detail()).containsEntry("to", "test@example.com");
        assertThat(result.detail()).containsEntry("subject", "Hello");
        assertThat(result.detail()).containsEntry("reason", "dry-run");
        assertThat(result.followAllEdges()).isTrue();
    }

    @Test
    void process_dryRun_resolvesEmailVariables() {
        AutomationNode node = buildNode("""
                {"to":"{{email.from}}","cc":"","bcc":"","subject":"Re: {{email.subject}}","body":"Dear {{email.fromName}}"}
                """);
        Email triggerEmail = createEmail(account.getId());
        triggerEmail.setFromAddress("sender@test.com");
        triggerEmail.setFromPersonal("Sender Name");
        triggerEmail.setSubject("Original Subject");
        ExecutionContext context = new ExecutionContext(triggerEmail, account, true)
                .withVariable("email.from", "sender@test.com")
                .withVariable("email.fromName", "Sender Name")
                .withVariable("email.subject", "Original Subject");

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.SIMULATED);
        assertThat(result.detail()).containsEntry("to", "sender@test.com");
        assertThat(result.detail()).containsEntry("subject", "Re: Original Subject");
        assertThat((String) result.detail().get("resolvedBody")).contains("Dear Sender Name");
    }

    @Test
    void process_dryRun_resolvesExtractedDataVariables() {
        AutomationNode node = buildNode("""
                {"to":"{{extraction_0.customerEmail}}","cc":"","bcc":"","subject":"Order {{extraction_0.orderId}}","body":"Thanks"}
                """);
        ExecutionContext context = new ExecutionContext(null, account, true)
                .withVariable("extraction_0.customerEmail", "customer@test.com")
                .withVariable("extraction_0.orderId", "12345");

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.SIMULATED);
        assertThat(result.detail()).containsEntry("to", "customer@test.com");
        assertThat(result.detail()).containsEntry("subject", "Order 12345");
    }

    @Test
    void process_smtpNotConfigured_returnsError() {
        account.setWriteEnabled(false);
        AutomationNode node = buildNode("""
                {"to":"test@example.com","cc":"","bcc":"","subject":"Hello","body":"Hi"}
                """);
        ExecutionContext context = new ExecutionContext(null, account, false);

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.ERROR);
        assertThat(result.detail()).containsKey("error");
        assertThat((String) result.detail().get("error")).contains("SMTP not configured");
    }

    @Test
    void process_realRun_sendsEmailAndReturnsExecuted() throws Exception {
        account.setWriteEnabled(true);
        account.setSmtpHost("smtp.example.com");
        Session session = Session.getInstance(new Properties());
        when(mailConnectionFactory.createSmtpSession(account)).thenReturn(session);

        AutomationNode node = buildNode("""
                {"to":"recipient@example.com","cc":"cc@example.com","bcc":"","subject":"Test","body":"<p>Hello</p>"}
                """);
        ExecutionContext context = new ExecutionContext(null, account, false);

        try (var transportMock = mockStatic(jakarta.mail.Transport.class)) {
            transportMock.when(() -> jakarta.mail.Transport.send(any(MimeMessage.class))).thenAnswer(inv -> null);

            NodeProcessorResult result = processor.process(node, context, userId);

            assertThat(result.status()).isEqualTo(NodeResultStatus.EXECUTED);
            assertThat(result.detail()).containsEntry("to", "recipient@example.com");
            assertThat(result.detail()).containsEntry("cc", "cc@example.com");
            assertThat(result.followAllEdges()).isTrue();
            verify(emailSyncService).appendToSentFolder(eq(account), any(MimeMessage.class));
        }
    }

    @Test
    void process_transportFails_returnsError() throws Exception {
        account.setWriteEnabled(true);
        account.setSmtpHost("smtp.example.com");
        Session session = Session.getInstance(new Properties());
        when(mailConnectionFactory.createSmtpSession(account)).thenReturn(session);

        AutomationNode node = buildNode("""
                {"to":"recipient@example.com","cc":"","bcc":"","subject":"Test","body":"Hi"}
                """);
        ExecutionContext context = new ExecutionContext(null, account, false);

        try (var transportMock = mockStatic(jakarta.mail.Transport.class)) {
            transportMock.when(() -> jakarta.mail.Transport.send(any(MimeMessage.class)))
                    .thenThrow(new jakarta.mail.MessagingException("SMTP connection refused"));

            NodeProcessorResult result = processor.process(node, context, userId);

            assertThat(result.status()).isEqualTo(NodeResultStatus.ERROR);
            assertThat((String) result.detail().get("error")).contains("SMTP connection refused");
            assertThat(result.haltTraversal()).isTrue();
        }
    }

    @Test
    void process_invalidJson_returnsError() {
        AutomationNode node = buildNode("invalid json");
        ExecutionContext context = new ExecutionContext(null, account, false);

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.ERROR);
        assertThat(result.detail()).containsKey("error");
    }

    @Test
    void process_emptyConfig_usesDefaults() {
        AutomationNode node = buildNode("{}");
        ExecutionContext context = new ExecutionContext(null, account, true);

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.SIMULATED);
        assertThat(result.detail()).containsEntry("to", "");
        assertThat(result.detail()).containsEntry("subject", "");
    }

    @Test
    void requiresEmailContext_isFalse() {
        assertThat(processor.requiresEmailContext()).isFalse();
    }

    @Test
    void process_dryRun_rendersTemplateSubjectAndBody() {
        UUID templateId = UUID.randomUUID();
        Template template = Template.builder()
                .id(templateId)
                .userId(userId)
                .name("Welcome")
                .subject("Hi {{trigger.name}}")
                .body("<p>Welcome {{trigger.name}}</p>")
                .build();
        when(templateRepository.findByIdAndUserId(templateId, userId)).thenReturn(Optional.of(template));

        AutomationNode node = buildNode("""
                {"to":"new@example.com","cc":"","bcc":"","templateId":"%s"}
                """.formatted(templateId));
        ExecutionContext context = new ExecutionContext(null, account, true)
                .withVariable("trigger.name", "Alice");

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.SIMULATED);
        assertThat(result.detail()).containsEntry("subject", "Hi Alice");
        assertThat(result.detail()).containsEntry("templateName", "Welcome");
        assertThat((String) result.detail().get("resolvedBody")).contains("Welcome Alice");
    }

    @Test
    void process_explicitSenderAccount_isResolvedFromConfig() {
        UUID senderId = UUID.randomUUID();
        EmailAccount sender = createEmailAccount(userId);
        sender.setId(senderId);
        sender.setEmail("sender@company.com");
        when(emailAccountRepository.findByIdAndUserId(senderId, userId)).thenReturn(Optional.of(sender));

        AutomationNode node = buildNode("""
                {"to":"new@example.com","cc":"","bcc":"","subject":"Hi","body":"Body","senderAccountId":"%s"}
                """.formatted(senderId));
        // No account in context (e.g. webhook-triggered automation)
        ExecutionContext context = new ExecutionContext(null, null, true);

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.SIMULATED);
        assertThat(result.detail()).containsEntry("senderAccount", "sender@company.com");
    }

    @Test
    void process_noSenderAccountAvailable_returnsError() {
        AutomationNode node = buildNode("""
                {"to":"new@example.com","cc":"","bcc":"","subject":"Hi","body":"Body"}
                """);
        ExecutionContext context = new ExecutionContext(null, null, true);

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.ERROR);
        assertThat((String) result.detail().get("error")).contains("No sender account");
    }

    @Test
    void process_senderAccountNotFound_returnsError() {
        UUID senderId = UUID.randomUUID();
        when(emailAccountRepository.findByIdAndUserId(senderId, userId)).thenReturn(Optional.empty());

        AutomationNode node = buildNode("""
                {"to":"new@example.com","cc":"","bcc":"","subject":"Hi","body":"Body","senderAccountId":"%s"}
                """.formatted(senderId));
        ExecutionContext context = new ExecutionContext(null, null, true);

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.ERROR);
        assertThat((String) result.detail().get("error")).contains("Sender account not found");
    }

    private AutomationNode buildNode(String config) {
        return AutomationNode.builder()
                .id(UUID.randomUUID())
                .nodeType(NodeType.SEND_EMAIL)
                .label("Send Email")
                .config(config)
                .positionX(0.0)
                .positionY(0.0)
                .createdAt(Instant.now())
                .build();
    }
}
