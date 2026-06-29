package com.postwerk.service.executor;

import com.postwerk.model.AutomationNode;
import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.model.enums.NodeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import java.util.Map;

import static com.postwerk.TestFixtures.createEmail;
import static com.postwerk.TestFixtures.createEmailAccount;
import static org.assertj.core.api.Assertions.assertThat;

class TriggerNodeProcessorTest {

    private TriggerNodeProcessor processor;
    private UUID userId;
    private EmailAccount account;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        processor = new TriggerNodeProcessor(objectMapper);
        userId = UUID.randomUUID();
        account = createEmailAccount(userId);
    }

    @Test
    void getNodeType_returnsTrigger() {
        assertThat(processor.getNodeType()).isEqualTo(NodeType.TRIGGER);
    }

    @Test
    void process_emailMode_newEmail_routesToNewEmailHandle() {
        Email email = createEmail(account.getId());
        email.setInReplyTo(null);
        ExecutionContext context = new ExecutionContext(email, account, false);

        NodeProcessorResult result = processor.process(buildNode("{\"triggerMode\":\"EMAIL\",\"accountIds\":[]}"), context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.PASSED);
        assertThat(result.activeHandles()).containsExactly("new-email");
        assertThat(result.detail()).containsEntry("isReply", false);
    }

    @Test
    void process_emailMode_seedsAttachmentsAsList() {
        Email email = createEmail(account.getId());
        email.setInReplyTo(null);
        email.setHasAttachments(true);
        email.setAttachments("[{\"name\":\"a.pdf\",\"contentType\":\"application/pdf\",\"size\":\"1 KB\"}]");
        ExecutionContext context = new ExecutionContext(email, account, false);

        NodeProcessorResult result = processor.process(buildNode("{\"triggerMode\":\"EMAIL\"}"), context, userId);

        ExecutionContext outCtx = result.contextByHandle().get("new-email");
        Object attachments = outCtx.getVariable("email.attachments");
        assertThat(attachments).isInstanceOf(java.util.List.class);
        assertThat((java.util.List<?>) attachments).hasSize(1);
    }

    @Test
    void process_emailMode_malformedAttachmentsJson_seedsEmptyList() {
        Email email = createEmail(account.getId());
        email.setInReplyTo(null);
        email.setHasAttachments(true);
        email.setAttachments("{not-json");
        ExecutionContext context = new ExecutionContext(email, account, false);

        NodeProcessorResult result = processor.process(buildNode("{\"triggerMode\":\"EMAIL\"}"), context, userId);

        ExecutionContext outCtx = result.contextByHandle().get("new-email");
        assertThat(outCtx.getVariable("email.attachments")).isInstanceOf(java.util.List.class);
        assertThat((java.util.List<?>) outCtx.getVariable("email.attachments")).isEmpty();
    }

    @Test
    void process_emailMode_replyEmail_routesToReplyHandle() {
        Email email = createEmail(account.getId());
        email.setInReplyTo("<original@example.com>");
        ExecutionContext context = new ExecutionContext(email, account, false);

        NodeProcessorResult result = processor.process(buildNode("{\"triggerMode\":\"EMAIL\"}"), context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.PASSED);
        assertThat(result.activeHandles()).containsExactly("reply");
        assertThat(result.detail()).containsEntry("isReply", true);
    }

    @Test
    void process_emailMode_emptyInReplyTo_treatedAsNewEmail() {
        Email email = createEmail(account.getId());
        email.setInReplyTo("");
        ExecutionContext context = new ExecutionContext(email, account, false);

        NodeProcessorResult result = processor.process(buildNode("{\"triggerMode\":\"EMAIL\"}"), context, userId);

        assertThat(result.activeHandles()).containsExactly("new-email");
    }

    @Test
    void process_defaultMode_treatedAsEmail() {
        Email email = createEmail(account.getId());
        email.setInReplyTo(null);
        ExecutionContext context = new ExecutionContext(email, account, false);

        NodeProcessorResult result = processor.process(buildNode("{}"), context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.PASSED);
        assertThat(result.activeHandles()).containsExactly("new-email");
    }

    @Test
    void process_cronMode_routesToOutput() {
        Email email = createEmail(account.getId());
        ExecutionContext context = new ExecutionContext(email, account, false);

        NodeProcessorResult result = processor.process(
                buildNode("{\"triggerMode\":\"CRON\",\"scheduleType\":\"INTERVAL\",\"intervalMinutes\":60}"),
                context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.PASSED);
        assertThat(result.activeHandles()).containsExactly("output");
    }

    @Test
    void process_webhookMode_inbound_passesThroughToOutputWithTriggerVars() {
        Email email = createEmail(account.getId());
        ExecutionContext context = new ExecutionContext(email, account, false)
                .withVariables(Map.of("trigger.orderId", "123", "trigger.body", "{}"));

        NodeProcessorResult result = processor.process(
                buildNode("{\"triggerMode\":\"WEBHOOK\"}"), context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.PASSED);
        assertThat(result.activeHandles()).containsExactly("output");
        // trigger.* vars are propagated on the output handle context
        ExecutionContext outCtx = result.contextByHandle().get("output");
        assertThat(outCtx).isNotNull();
        assertThat(outCtx.getVariable("trigger.orderId")).isEqualTo("123");
    }

    @Test
    void process_manualMode_passesThroughToOutputWithTriggerVars() {
        Email email = createEmail(account.getId());
        ExecutionContext context = new ExecutionContext(email, account, false)
                .withVariables(Map.of("trigger.amount", "500"));

        NodeProcessorResult result = processor.process(
                buildNode("{\"triggerMode\":\"MANUAL\"}"), context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.PASSED);
        assertThat(result.activeHandles()).containsExactly("output");
        ExecutionContext outCtx = result.contextByHandle().get("output");
        assertThat(outCtx).isNotNull();
        assertThat(outCtx.getVariable("trigger.amount")).isEqualTo("500");
    }

    @Test
    void process_webhookMode_dryRun_isSimulated() {
        Email email = createEmail(account.getId());
        ExecutionContext context = new ExecutionContext(email, account, true);

        NodeProcessorResult result = processor.process(
                buildNode("{\"triggerMode\":\"WEBHOOK\"}"), context, userId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.SIMULATED);
        assertThat(result.activeHandles()).containsExactly("output");
    }

    private AutomationNode buildNode(String config) {
        return AutomationNode.builder()
                .id(UUID.randomUUID())
                .nodeType(NodeType.TRIGGER)
                .label("Trigger")
                .config(config)
                .positionX(0.0).positionY(0.0)
                .createdAt(Instant.now())
                .build();
    }
}
