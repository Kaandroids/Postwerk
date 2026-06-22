package com.postwerk.service.executor;

import com.postwerk.model.AutomationNode;
import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.postwerk.model.Template;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.model.enums.NodeType;
import com.postwerk.repository.TemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.postwerk.TestFixtures.createEmail;
import static com.postwerk.TestFixtures.createEmailAccount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailActionNodeProcessorTest {

    @Mock private ActionExecutor replyExecutor;
    @Mock private ActionExecutor forwardExecutor;
    @Mock private ActionExecutor moveExecutor;
    @Mock private ActionExecutor trashExecutor;
    @Mock private TemplateRepository templateRepository;

    private EmailActionNodeProcessor processor;
    private UUID userId;
    private EmailAccount account;
    private Email email;

    @BeforeEach
    void setUp() {
        lenient().when(replyExecutor.getActionType()).thenReturn("REPLY_TEMPLATE");
        lenient().when(forwardExecutor.getActionType()).thenReturn("FORWARD");
        lenient().when(moveExecutor.getActionType()).thenReturn("MOVE_FOLDER");
        lenient().when(trashExecutor.getActionType()).thenReturn("TRASH");
        processor = new EmailActionNodeProcessor(
                List.of(replyExecutor, forwardExecutor, moveExecutor, trashExecutor),
                templateRepository, new ObjectMapper(), new VariableResolver());
        userId = UUID.randomUUID();
        account = createEmailAccount(userId);
        email = createEmail(account.getId());
    }

    @Test
    void getNodeType_returnsEmailAction() {
        assertThat(processor.getNodeType()).isEqualTo(NodeType.EMAIL_ACTION);
    }

    // ── REPLY mode tests ──────────────────────────────

    @Nested
    class ReplyMode {

        @Test
        void dryRun_returnsSimulated() {
            AutomationNode node = buildNode("{\"actionMode\":\"REPLY\"}");
            ExecutionContext context = new ExecutionContext(email, account, true);

            NodeProcessorResult result = processor.process(node, context, userId);

            assertThat(result.status()).isEqualTo(NodeResultStatus.SIMULATED);
            assertThat(result.detail()).containsEntry("actionMode", "REPLY");
            assertThat(result.detail()).containsEntry("reason", "dry-run");
        }

        @Test
        void dryRun_withTemplate_rendersPreview() {
            UUID templateId = UUID.randomUUID();
            Template template = Template.builder()
                    .id(templateId)
                    .name("Welcome Template")
                    .subject("Re: {{subject}}")
                    .body("Hello {{fromName}}")
                    .build();
            when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));

            AutomationNode node = buildNode("{\"actionMode\":\"REPLY\",\"contentSource\":\"VORLAGE\",\"templateId\":\"%s\"}".formatted(templateId));
            ExecutionContext context = new ExecutionContext(email, account, true)
                    .withVariable("email.subject", email.getSubject())
                    .withVariable("email.fromName", email.getFromPersonal());

            NodeProcessorResult result = processor.process(node, context, userId);

            assertThat(result.status()).isEqualTo(NodeResultStatus.SIMULATED);
            assertThat(result.detail()).containsEntry("contentSource", "VORLAGE");
            assertThat(result.detail()).containsEntry("templateName", "Welcome Template");
            assertThat((String) result.detail().get("renderedSubject")).contains("Test Email Subject");
            assertThat((String) result.detail().get("renderedBody")).contains("Sender Name");
        }

        @Test
        void dryRun_manualContent_rendersPreview() {
            AutomationNode node = buildNode(
                    "{\"actionMode\":\"REPLY\",\"contentSource\":\"MANUAL\",\"subject\":\"Re: {{email.subject}}\",\"body\":\"Hi {{email.fromName}}\"}");
            ExecutionContext context = new ExecutionContext(email, account, true)
                    .withVariable("email.subject", email.getSubject())
                    .withVariable("email.fromName", email.getFromPersonal());

            NodeProcessorResult result = processor.process(node, context, userId);

            assertThat(result.status()).isEqualTo(NodeResultStatus.SIMULATED);
            assertThat(result.detail()).containsEntry("contentSource", "MANUAL");
            assertThat((String) result.detail().get("renderedSubject")).contains("Test Email Subject");
            assertThat((String) result.detail().get("renderedBody")).contains("Sender Name");
            verifyNoInteractions(templateRepository);
        }

        @Test
        void realRun_callsReplyExecutor() throws Exception {
            AutomationNode node = buildNode("{\"actionMode\":\"REPLY\"}");
            ExecutionContext context = new ExecutionContext(email, account, false);

            NodeProcessorResult result = processor.process(node, context, userId);

            assertThat(result.status()).isEqualTo(NodeResultStatus.EXECUTED);
            assertThat(result.detail()).containsEntry("actionMode", "REPLY");
            verify(replyExecutor).execute(eq(email), eq(account), any(), eq(context));
        }

        @Test
        void missingActionMode_defaultsToReply() {
            AutomationNode node = buildNode("{}");
            ExecutionContext context = new ExecutionContext(email, account, true);

            NodeProcessorResult result = processor.process(node, context, userId);

            assertThat(result.detail()).containsEntry("actionMode", "REPLY");
        }
    }

    // ── FORWARD mode tests ────────────────────────────

    @Nested
    class ForwardMode {

        @Test
        void dryRun_returnsSimulatedWithToAddress() {
            AutomationNode node = buildNode("{\"actionMode\":\"FORWARD\",\"toAddress\":\"forward@example.com\"}");
            ExecutionContext context = new ExecutionContext(email, account, true);

            NodeProcessorResult result = processor.process(node, context, userId);

            assertThat(result.status()).isEqualTo(NodeResultStatus.SIMULATED);
            assertThat(result.detail()).containsEntry("actionMode", "FORWARD");
            assertThat(result.detail()).containsEntry("toAddress", "forward@example.com");
            assertThat(result.detail()).containsEntry("reason", "dry-run");
        }

        @Test
        void dryRun_manualContent_rendersPreview() {
            AutomationNode node = buildNode(
                    "{\"actionMode\":\"FORWARD\",\"toAddress\":\"forward@example.com\",\"contentSource\":\"MANUAL\",\"subject\":\"FYI {{email.subject}}\",\"body\":\"Bitte ansehen\"}");
            ExecutionContext context = new ExecutionContext(email, account, true)
                    .withVariable("email.subject", email.getSubject());

            NodeProcessorResult result = processor.process(node, context, userId);

            assertThat(result.status()).isEqualTo(NodeResultStatus.SIMULATED);
            assertThat(result.detail()).containsEntry("contentSource", "MANUAL");
            assertThat((String) result.detail().get("renderedSubject")).contains("Test Email Subject");
            assertThat(result.detail()).containsEntry("renderedBody", "Bitte ansehen");
            verifyNoInteractions(templateRepository);
        }

        @Test
        void realRun_callsForwardExecutor() throws Exception {
            AutomationNode node = buildNode("{\"actionMode\":\"FORWARD\",\"toAddress\":\"forward@example.com\"}");
            ExecutionContext context = new ExecutionContext(email, account, false);

            NodeProcessorResult result = processor.process(node, context, userId);

            assertThat(result.status()).isEqualTo(NodeResultStatus.EXECUTED);
            assertThat(result.detail()).containsEntry("actionMode", "FORWARD");
            assertThat(result.detail()).containsEntry("toAddress", "forward@example.com");
            verify(forwardExecutor).execute(eq(email), eq(account), any(), eq(context));
        }
    }

    // ── MOVE_FOLDER mode tests ────────────────────────

    @Nested
    class MoveFolderMode {

        @Test
        void dryRun_returnsSimulatedWithFolder() {
            AutomationNode node = buildNode("{\"actionMode\":\"MOVE_FOLDER\",\"folder\":\"Archive\"}");
            ExecutionContext context = new ExecutionContext(email, account, true);

            NodeProcessorResult result = processor.process(node, context, userId);

            assertThat(result.status()).isEqualTo(NodeResultStatus.SIMULATED);
            assertThat(result.detail()).containsEntry("actionMode", "MOVE_FOLDER");
            assertThat(result.detail()).containsEntry("folder", "Archive");
            assertThat(result.detail()).containsEntry("isTrash", false);
        }

        @Test
        void dryRun_trash_returnsSimulatedWithTrashFlag() {
            AutomationNode node = buildNode("{\"actionMode\":\"MOVE_FOLDER\",\"folder\":\"__TRASH__\"}");
            ExecutionContext context = new ExecutionContext(email, account, true);

            NodeProcessorResult result = processor.process(node, context, userId);

            assertThat(result.status()).isEqualTo(NodeResultStatus.SIMULATED);
            assertThat(result.detail()).containsEntry("isTrash", true);
        }

        @Test
        void realRun_moveFolder_callsMoveExecutor() throws Exception {
            AutomationNode node = buildNode("{\"actionMode\":\"MOVE_FOLDER\",\"folder\":\"Archive\"}");
            ExecutionContext context = new ExecutionContext(email, account, false);

            NodeProcessorResult result = processor.process(node, context, userId);

            assertThat(result.status()).isEqualTo(NodeResultStatus.EXECUTED);
            verify(moveExecutor).execute(eq(email), eq(account), any(), eq(context));
        }

        @Test
        void realRun_trash_callsTrashExecutor() throws Exception {
            AutomationNode node = buildNode("{\"actionMode\":\"MOVE_FOLDER\",\"folder\":\"__TRASH__\"}");
            ExecutionContext context = new ExecutionContext(email, account, false);

            NodeProcessorResult result = processor.process(node, context, userId);

            assertThat(result.status()).isEqualTo(NodeResultStatus.EXECUTED);
            verify(trashExecutor).execute(eq(email), eq(account), any(), eq(context));
        }
    }

    // ── Error handling ────────────────────────────────

    @Test
    void invalidConfig_returnsError() {
        AutomationNode node = buildNode("bad json");
        ExecutionContext context = new ExecutionContext(email, account, false);

        NodeProcessorResult result = processor.process(node, context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.ERROR);
        assertThat(result.detail()).containsKey("error");
    }

    private AutomationNode buildNode(String config) {
        return AutomationNode.builder()
                .id(UUID.randomUUID())
                .nodeType(NodeType.EMAIL_ACTION)
                .label("Email Action")
                .config(config)
                .positionX(0.0).positionY(0.0)
                .createdAt(Instant.now())
                .build();
    }
}
