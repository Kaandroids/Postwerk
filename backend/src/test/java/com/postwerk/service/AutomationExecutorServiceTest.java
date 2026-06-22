package com.postwerk.service;

import com.postwerk.TestFixtures;
import com.postwerk.model.*;
import com.postwerk.model.enums.*;
import com.postwerk.repository.*;
import com.postwerk.repository.AutomationDelayedEmailRepository;
import com.postwerk.service.executor.*;
import com.postwerk.service.impl.AutomationExecutorServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutomationExecutorServiceTest {

    @Mock private AutomationRepository automationRepository;
    @Mock private AutomationNodeRepository nodeRepository;
    @Mock private AutomationEdgeRepository edgeRepository;
    @Mock private AutomationExecutionRepository executionRepository;
    @Mock private EmailRepository emailRepository;
    @Mock private EmailAccountRepository emailAccountRepository;
    @Mock private EmailSyncService emailSyncService;
    @Mock private EmailAutomationTraceService traceService;
    @Mock private EmailAutomationTraceRepository traceRepository;
    @Mock private AuditService auditService;
    @Mock private TestModeService testModeService;
    @Mock private VariableFilterEvaluator variableFilterEvaluator;
    @Mock private ExtractNodeExecutor extractNodeExecutor;
    @Mock private CategorizeNodeExecutor categorizeNodeExecutor;
    @Mock private DelayNodeExecutor delayNodeExecutor;
    @Mock private LabelNodeExecutor labelNodeExecutor;
    @Mock private RemoveLabelNodeExecutor removeLabelNodeExecutor;
    @Mock private AutomationDelayedEmailRepository delayedEmailRepository;
    @Mock private TemplateRepository templateRepository;
    @Mock private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    @Mock private org.springframework.data.redis.core.ValueOperations<String, String> valueOps;
    @Mock private com.postwerk.service.PendingActionService pendingActionService;
    @Mock private com.postwerk.service.JobRunService jobRunService;

    private AutomationExecutorServiceImpl executor;
    private ObjectMapper objectMapper;

    private UUID userId;
    private EmailAccount account;
    private Email email;
    private Automation automation;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        List<NodeProcessor> processors = List.of(
                new TriggerNodeProcessor(objectMapper),
                new FilterNodeProcessor(variableFilterEvaluator),
                new ExtractNodeProcessor(extractNodeExecutor, objectMapper),
                new CategorizeNodeProcessor(categorizeNodeExecutor, objectMapper),
                new DelayNodeProcessor(delayNodeExecutor, objectMapper),
                new LabelNodeProcessor(labelNodeExecutor, objectMapper),
                new EmailActionNodeProcessor(List.of(), templateRepository, objectMapper, new VariableResolver()),
                new RemoveLabelNodeProcessor(removeLabelNodeExecutor, objectMapper)
        );

        org.mockito.Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        org.mockito.Mockito.lenient().when(valueOps.setIfAbsent(any(), any(), any(java.time.Duration.class))).thenReturn(true);
        org.mockito.Mockito.lenient().when(redisTemplate.delete(any(String.class))).thenReturn(true);
        // JobRunService wraps scheduled jobs — invoke the wrapped work inline so the existing tests run unchanged.
        org.mockito.Mockito.lenient().doAnswer(inv -> { ((Runnable) inv.getArgument(1)).run(); return null; })
                .when(jobRunService).run(any(), any());
        org.mockito.Mockito.lenient().doAnswer(inv -> { ((Runnable) inv.getArgument(1)).run(); return null; })
                .when(jobRunService).runManual(any(), any(), any());

        var encryption = new com.postwerk.config.EncryptionConfig(
                java.util.Base64.getEncoder().encodeToString(new byte[32]),
                new org.springframework.mock.env.MockEnvironment());
        var constantsCodec = new AutomationConstantsCodec(objectMapper, encryption);
        executor = new AutomationExecutorServiceImpl(
                automationRepository, nodeRepository, edgeRepository, executionRepository,
                delayedEmailRepository, emailRepository, emailAccountRepository, emailSyncService,
                traceService, traceRepository, auditService, testModeService,
                processors, objectMapper, redisTemplate, constantsCodec, pendingActionService,
                jobRunService,
                Runnable::run,  // run dispatched flow inline (no async) in unit tests
                mock(org.springframework.context.ApplicationEventPublisher.class)
        );

        userId = UUID.randomUUID();
        account = TestFixtures.createEmailAccount(userId);
        email = TestFixtures.createEmail(account.getId());
        automation = TestFixtures.createAutomation(userId);
        automation.setAccountIds(new UUID[]{account.getId()});
    }

    @Test
    void pollAndExecute_noAutomationsDue_doesNothing() {
        when(automationRepository.findDueAutomations(any(), any())).thenReturn(List.of());

        executor.pollAndExecute();

        verify(emailSyncService, never()).sync(any());
    }

    @Test
    void pollAndExecute_findsDueAutomations_syncsAndProcesses() {
        when(automationRepository.findDueAutomations(any(), any())).thenReturn(List.of(automation));
        when(emailAccountRepository.findAllById(anySet())).thenReturn(List.of(account));
        when(emailRepository.findTop100ByEmailAccountIdAndProcessedFalseOrderByReceivedAtDesc(account.getId()))
                .thenReturn(List.of());

        executor.pollAndExecute();

        verify(emailSyncService).sync(account);
        verify(automationRepository).saveAll(anyList());
    }

    @Test
    void processEmail_alreadyProcessed_skips() {
        email.setProcessed(true);

        executor.processEmail(email);

        verify(automationRepository, never()).findProcessableByAccountId(any());
    }

    @Test
    void processEmail_noActiveAutomations_marksProcessed() {
        when(automationRepository.findProcessableByAccountId(email.getEmailAccountId())).thenReturn(List.of());

        executor.processEmail(email);

        assertThat(email.isProcessed()).isTrue();
        verify(emailRepository).save(email);
    }

    @Test
    void processEmail_noAccount_marksProcessed() {
        when(automationRepository.findProcessableByAccountId(email.getEmailAccountId()))
                .thenReturn(List.of(automation));
        when(emailAccountRepository.findById(email.getEmailAccountId())).thenReturn(Optional.empty());

        executor.processEmail(email);

        assertThat(email.isProcessed()).isTrue();
    }

    @Test
    void processEmailThroughAutomation_alreadyTraced_skips() {
        when(automationRepository.findProcessableByAccountId(email.getEmailAccountId()))
                .thenReturn(List.of(automation));
        when(emailAccountRepository.findById(email.getEmailAccountId())).thenReturn(Optional.of(account));
        when(traceRepository.existsByEmailIdAndAutomationId(email.getId(), automation.getId())).thenReturn(true);

        executor.processEmail(email);

        verify(traceService, never()).startTrace(any(), any(), any());
    }

    @Test
    void processEmailThroughAutomation_triggerNode_followsEdges() {
        setupAutomationExecution();

        var trigger = TestFixtures.createNode(automation, NodeType.TRIGGER, "Email Trigger");
        when(nodeRepository.findByAutomationId(automation.getId())).thenReturn(List.of(trigger));
        when(edgeRepository.findByAutomationId(automation.getId())).thenReturn(List.of());

        executor.processEmail(email);

        verify(traceService).addNodeTrace(any(), eq(trigger), eq(NodeResultStatus.PASSED), any());
        verify(traceService).completeTrace(any(), eq("SUCCESS"), isNull());
    }

    @Test
    void processEmailThroughAutomation_filterNode_matchFollowsTrue() {
        setupAutomationExecution();

        var trigger = TestFixtures.createNode(automation, NodeType.TRIGGER, "Trigger");
        var filter = TestFixtures.createNode(automation, NodeType.FILTER, "Filter");
        filter.setConfig("{\"checks\":[{\"label\":\"Test\",\"groups\":[{\"conditions\":[{\"field\":\"email.from\",\"operator\":\"CONTAINS\",\"value\":\"example\"}]}]}]}");

        var edge = TestFixtures.createEdge(automation, trigger, filter, "new-email");

        when(nodeRepository.findByAutomationId(automation.getId())).thenReturn(List.of(trigger, filter));
        when(edgeRepository.findByAutomationId(automation.getId())).thenReturn(List.of(edge));
        when(variableFilterEvaluator.evaluate(anyString(), anyMap()))
                .thenReturn(new VariableFilterEvaluator.VariableFilterResult(0, List.of()));

        executor.processEmail(email);

        verify(traceService).addNodeTrace(any(), eq(filter), eq(NodeResultStatus.MATCHED), any());
    }

    @Test
    void processEmail_reviewMode_parksActionInsteadOfExecuting() {
        setupAutomationExecution();

        var trigger = TestFixtures.createNode(automation, NodeType.TRIGGER, "Trigger");
        var action = TestFixtures.createNode(automation, NodeType.EMAIL_ACTION, "Reply");
        action.setConfig("{\"actionMode\":\"REPLY\",\"contentSource\":\"MANUAL\",\"subject\":\"Hi\",\"body\":\"<p>x</p>\",\"executionMode\":\"REVIEW\"}");
        var edge = TestFixtures.createEdge(automation, trigger, action, "new-email");

        when(nodeRepository.findByAutomationId(automation.getId())).thenReturn(List.of(trigger, action));
        when(edgeRepository.findByAutomationId(automation.getId())).thenReturn(List.of(edge));

        executor.processEmail(email);

        // Side effect is parked, not performed: park() is called and the node records PENDING_APPROVAL.
        verify(pendingActionService).park(eq(action), anyMap(), any(), any());
        verify(traceService).addNodeTrace(any(), eq(action), eq(NodeResultStatus.PENDING_APPROVAL), any());
    }

    @Test
    void processEmail_reviewWithThreshold_butNoUpstreamConfidence_stillParks() {
        setupAutomationExecution();

        var trigger = TestFixtures.createNode(automation, NodeType.TRIGGER, "Trigger");
        var action = TestFixtures.createNode(automation, NodeType.EMAIL_ACTION, "Reply");
        // REVIEW with a confidence threshold but no upstream CATEGORIZE → conservative: still parks.
        action.setConfig("{\"actionMode\":\"REPLY\",\"contentSource\":\"MANUAL\",\"subject\":\"Hi\",\"body\":\"x\",\"executionMode\":\"REVIEW\",\"reviewThreshold\":80}");
        var edge = TestFixtures.createEdge(automation, trigger, action, "new-email");

        when(nodeRepository.findByAutomationId(automation.getId())).thenReturn(List.of(trigger, action));
        when(edgeRepository.findByAutomationId(automation.getId())).thenReturn(List.of(edge));

        executor.processEmail(email);

        verify(pendingActionService).park(eq(action), anyMap(), any(), any());
        verify(traceService).addNodeTrace(any(), eq(action), eq(NodeResultStatus.PENDING_APPROVAL), any());
    }

    @Test
    void processEmail_offMode_skipsActionWithoutParking() {
        setupAutomationExecution();

        var trigger = TestFixtures.createNode(automation, NodeType.TRIGGER, "Trigger");
        var action = TestFixtures.createNode(automation, NodeType.EMAIL_ACTION, "Reply");
        action.setConfig("{\"actionMode\":\"REPLY\",\"contentSource\":\"MANUAL\",\"subject\":\"Hi\",\"body\":\"x\",\"executionMode\":\"OFF\"}");
        var edge = TestFixtures.createEdge(automation, trigger, action, "new-email");

        when(nodeRepository.findByAutomationId(automation.getId())).thenReturn(List.of(trigger, action));
        when(edgeRepository.findByAutomationId(automation.getId())).thenReturn(List.of(edge));

        executor.processEmail(email);

        verify(pendingActionService, never()).park(any(), anyMap(), any(), any());
        verify(traceService).addNodeTrace(any(), eq(action), eq(NodeResultStatus.SKIPPED), any());
    }

    @Test
    void processEmailThroughAutomation_filterNode_noMatchFollowsFalse() {
        setupAutomationExecution();

        var trigger = TestFixtures.createNode(automation, NodeType.TRIGGER, "Trigger");
        var filter = TestFixtures.createNode(automation, NodeType.FILTER, "Filter");
        filter.setConfig("{}");

        var edge = TestFixtures.createEdge(automation, trigger, filter, "new-email");

        when(nodeRepository.findByAutomationId(automation.getId())).thenReturn(List.of(trigger, filter));
        when(edgeRepository.findByAutomationId(automation.getId())).thenReturn(List.of(edge));
        when(variableFilterEvaluator.evaluate(anyString(), anyMap()))
                .thenReturn(new VariableFilterEvaluator.VariableFilterResult(-1, List.of()));

        executor.processEmail(email);

        verify(traceService).addNodeTrace(any(), eq(filter), eq(NodeResultStatus.NOT_MATCHED), any());
    }

    @Test
    void processEmailThroughAutomation_error_setsFailedStatus() {
        setupAutomationExecution();
        when(nodeRepository.findByAutomationId(automation.getId())).thenThrow(new RuntimeException("DB error"));

        executor.processEmail(email);

        verify(traceService).completeTrace(any(), eq("FAILED"), contains("DB error"));
        verify(executionRepository, times(1)).save(argThat(e -> e.getStatus() == ExecutionStatus.FAILED));
    }

    @Test
    void processEmailThroughAutomation_noTriggerNode_fails() {
        setupAutomationExecution();
        when(nodeRepository.findByAutomationId(automation.getId())).thenReturn(List.of());
        when(edgeRepository.findByAutomationId(automation.getId())).thenReturn(List.of());

        executor.processEmail(email);

        verify(traceService).completeTrace(any(), eq("FAILED"), contains("No TRIGGER node found"));
    }

    @Test
    void processEmailThroughAutomation_createsExecutionRecord() {
        setupAutomationExecution();
        var trigger = TestFixtures.createNode(automation, NodeType.TRIGGER, "Trigger");
        when(nodeRepository.findByAutomationId(automation.getId())).thenReturn(List.of(trigger));
        when(edgeRepository.findByAutomationId(automation.getId())).thenReturn(List.of());

        executor.processEmail(email);

        verify(executionRepository, atLeastOnce()).save(any(AutomationExecution.class));
    }

    @Test
    void pollAndExecute_batchFetchAccounts() {
        var auto2 = TestFixtures.createAutomation(userId);
        UUID accountId2 = UUID.randomUUID();
        auto2.setAccountIds(new UUID[]{accountId2});

        when(automationRepository.findDueAutomations(any(), any())).thenReturn(List.of(automation, auto2));
        var account2 = TestFixtures.createEmailAccount(userId);
        account2.setId(accountId2);
        when(emailAccountRepository.findAllById(anySet())).thenReturn(List.of(account, account2));
        when(emailRepository.findTop100ByEmailAccountIdAndProcessedFalseOrderByReceivedAtDesc(any()))
                .thenReturn(List.of());

        executor.pollAndExecute();

        verify(emailSyncService, times(2)).sync(any());
    }

    @Test
    void pollAndExecute_batchSaveAutomations() {
        when(automationRepository.findDueAutomations(any(), any())).thenReturn(List.of(automation));
        when(emailAccountRepository.findAllById(anySet())).thenReturn(List.of(account));
        when(emailRepository.findTop100ByEmailAccountIdAndProcessedFalseOrderByReceivedAtDesc(any()))
                .thenReturn(List.of());

        executor.pollAndExecute();

        verify(automationRepository).saveAll(List.of(automation));
        assertThat(automation.getLastRunAt()).isNotNull();
    }

    @Test
    void processEmailThroughAutomation_dagTraversal_multipleNodes() {
        setupAutomationExecution();

        var trigger = TestFixtures.createNode(automation, NodeType.TRIGGER, "Trigger");
        var filter = TestFixtures.createNode(automation, NodeType.FILTER, "Filter");
        filter.setConfig("{}");

        var edge1 = TestFixtures.createEdge(automation, trigger, filter, "new-email");

        when(nodeRepository.findByAutomationId(automation.getId())).thenReturn(List.of(trigger, filter));
        when(edgeRepository.findByAutomationId(automation.getId())).thenReturn(List.of(edge1));
        when(variableFilterEvaluator.evaluate(anyString(), anyMap()))
                .thenReturn(new VariableFilterEvaluator.VariableFilterResult(0, List.of()));

        executor.processEmail(email);

        verify(traceService).addNodeTrace(any(), eq(trigger), eq(NodeResultStatus.PASSED), any());
        verify(traceService).addNodeTrace(any(), eq(filter), eq(NodeResultStatus.MATCHED), any());
    }

    @Test
    void processEmailThroughAutomation_cronTrigger_followsEdges() {
        setupAutomationExecution();

        var trigger = TestFixtures.createNode(automation, NodeType.TRIGGER, "Trigger");
        trigger.setConfig("{\"triggerMode\":\"CRON\",\"scheduleType\":\"INTERVAL\",\"intervalMinutes\":60}");
        when(nodeRepository.findByAutomationId(automation.getId())).thenReturn(List.of(trigger));
        when(edgeRepository.findByAutomationId(automation.getId())).thenReturn(List.of());

        executor.processEmail(email);

        verify(traceService).addNodeTrace(any(), eq(trigger), eq(NodeResultStatus.PASSED), any());
        verify(traceService).completeTrace(any(), eq("SUCCESS"), isNull());
    }

    @Test
    void processEmailThroughAutomation_labelNode_executesLabels() {
        setupAutomationExecution();

        var trigger = TestFixtures.createNode(automation, NodeType.TRIGGER, "Trigger");
        var label = TestFixtures.createNode(automation, NodeType.LABEL, "Label");
        label.setConfig("{\"categoryId\":\"" + UUID.randomUUID() + "\"}");

        var edge = TestFixtures.createEdge(automation, trigger, label, "new-email");

        when(nodeRepository.findByAutomationId(automation.getId())).thenReturn(List.of(trigger, label));
        when(edgeRepository.findByAutomationId(automation.getId())).thenReturn(List.of(edge));
        when(labelNodeExecutor.execute(eq(email), any(), eq(false)))
                .thenReturn(Map.of("categoryName", "Orders", "categoryColor", "#3b82f6"));

        executor.processEmail(email);

        verify(traceService).addNodeTrace(any(), eq(label), eq(NodeResultStatus.EXECUTED), any());
    }

    @Test
    void processEmailThroughAutomation_delayNode_queuesEmail() {
        setupAutomationExecution();

        var trigger = TestFixtures.createNode(automation, NodeType.TRIGGER, "Trigger");
        var delay = TestFixtures.createNode(automation, NodeType.DELAY, "Delay");
        delay.setConfig("{\"delayMinutes\":30}");

        var edge = TestFixtures.createEdge(automation, trigger, delay, "new-email");

        when(nodeRepository.findByAutomationId(automation.getId())).thenReturn(List.of(trigger, delay));
        when(edgeRepository.findByAutomationId(automation.getId())).thenReturn(List.of(edge));
        when(delayNodeExecutor.execute(eq(email), any(), eq(delay.getId()), any(), eq(false)))
                .thenReturn(Map.of("delayMinutes", 30, "delayedUntil", "2024-06-16T10:30:00Z"));

        executor.processEmail(email);

        verify(traceService).addNodeTrace(any(), eq(delay), eq(NodeResultStatus.EXECUTED), any());
    }

    // ─── Inbound webhook: account-less runs ───────────────────────

    @Test
    void runInboundWebhook_accountLess_persistsNoEmailAndTracesNullEmail() {
        var trigger = TestFixtures.createNode(automation, NodeType.TRIGGER, "Webhook Trigger");
        trigger.setConfig("{\"triggerMode\":\"WEBHOOK\"}");
        when(nodeRepository.findByAutomationId(automation.getId())).thenReturn(List.of(trigger));
        when(edgeRepository.findByAutomationId(automation.getId())).thenReturn(List.of());

        var execution = AutomationExecution.builder()
                .id(UUID.randomUUID()).automationId(automation.getId())
                .status(ExecutionStatus.RUNNING).processedCount(0).build();
        when(executionRepository.save(any(AutomationExecution.class))).thenReturn(execution);
        var trace = EmailAutomationTrace.builder().id(UUID.randomUUID()).build();
        when(traceService.startTrace(isNull(), eq(automation), any())).thenReturn(trace);

        UUID executionId = executor.runInboundWebhook(automation, trigger.getId(), null, null,
                Map.of("trigger.orderId", "123"));

        assertThat(executionId).isEqualTo(execution.getId());
        verify(emailRepository, never()).save(any());
        verify(traceService).startTrace(isNull(), eq(automation), any());
        verify(traceService).completeTrace(eq(trace), eq("SUCCESS"), isNull());
    }

    @Test
    void runInboundWebhook_accountLess_emailBoundNode_recordsErrorAndHalts() {
        var trigger = TestFixtures.createNode(automation, NodeType.TRIGGER, "Webhook Trigger");
        trigger.setConfig("{\"triggerMode\":\"WEBHOOK\"}");
        var label = TestFixtures.createNode(automation, NodeType.LABEL, "Label");
        var edge = TestFixtures.createEdge(automation, trigger, label, "output");

        when(nodeRepository.findByAutomationId(automation.getId())).thenReturn(List.of(trigger, label));
        when(edgeRepository.findByAutomationId(automation.getId())).thenReturn(List.of(edge));

        var execution = AutomationExecution.builder()
                .id(UUID.randomUUID()).automationId(automation.getId())
                .status(ExecutionStatus.RUNNING).processedCount(0).build();
        when(executionRepository.save(any(AutomationExecution.class))).thenReturn(execution);
        var trace = EmailAutomationTrace.builder().id(UUID.randomUUID()).build();
        when(traceService.startTrace(isNull(), eq(automation), any())).thenReturn(trace);

        executor.runInboundWebhook(automation, trigger.getId(), null, null, Map.of());

        verify(traceService).addNodeTrace(any(), eq(label), eq(NodeResultStatus.ERROR), any());
        verify(labelNodeExecutor, never()).execute(any(), any(), anyBoolean());
    }

    private void setupAutomationExecution() {
        when(automationRepository.findProcessableByAccountId(email.getEmailAccountId()))
                .thenReturn(List.of(automation));
        when(emailAccountRepository.findById(email.getEmailAccountId())).thenReturn(Optional.of(account));
        when(traceRepository.existsByEmailIdAndAutomationId(email.getId(), automation.getId())).thenReturn(false);

        var execution = AutomationExecution.builder()
                .id(UUID.randomUUID())
                .automationId(automation.getId())
                .status(ExecutionStatus.RUNNING)
                .processedCount(0)
                .build();
        when(executionRepository.save(any(AutomationExecution.class))).thenReturn(execution);

        var trace = EmailAutomationTrace.builder().id(UUID.randomUUID()).build();
        when(traceService.startTrace(any(), any(), any())).thenReturn(trace);
    }
}
