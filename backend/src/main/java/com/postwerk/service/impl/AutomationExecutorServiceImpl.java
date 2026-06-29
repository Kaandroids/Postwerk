package com.postwerk.service.impl;

import com.postwerk.dto.automation.NodeMock;
import com.postwerk.event.AutomationFailedEvent;
import com.postwerk.model.*;
import com.postwerk.model.enums.AutomationKind;
import com.postwerk.model.enums.AutomationStatus;
import com.postwerk.model.enums.ExecutionStatus;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.model.enums.NodeType;
import com.postwerk.model.AutomationDelayedEmail;
import com.postwerk.repository.*;
import com.postwerk.service.AuditService;
import com.postwerk.service.AutomationConstantsCodec;
import com.postwerk.service.AutomationExecutorService;
import com.postwerk.model.PendingAction;
import com.postwerk.service.EmailAutomationTraceService;
import com.postwerk.service.PendingActionService;
import com.postwerk.service.TestModeService;
import com.postwerk.repository.EmailAutomationTraceRepository;
import com.postwerk.service.EmailSyncService;
import com.postwerk.service.JobRunService;
import com.postwerk.service.executor.ExecutionContext;
import com.postwerk.service.executor.NodeProcessor;
import com.postwerk.service.executor.NodeProcessorResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Scheduled automation execution engine.
 *
 * <p>Polls for active automations on a fixed interval, synchronizes associated email accounts
 * via IMAP, and processes unprocessed emails through their respective automation graphs.
 * Traverses the node DAG (trigger, filter, extract, categorize, action) recursively,
 * delegating to specialized node executors and recording per-node execution traces.</p>
 *
 * @since 1.0
 */
@Service
public class AutomationExecutorServiceImpl implements AutomationExecutorService {

    private static final Logger log = LoggerFactory.getLogger(AutomationExecutorServiceImpl.class);

    private final AutomationRepository automationRepository;
    private final AutomationNodeRepository nodeRepository;
    private final AutomationEdgeRepository edgeRepository;
    private final AutomationExecutionRepository executionRepository;
    private final AutomationDelayedEmailRepository delayedEmailRepository;
    private final EmailRepository emailRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final EmailSyncService emailSyncService;
    private final EmailAutomationTraceService traceService;
    private final EmailAutomationTraceRepository traceRepository;
    private final AuditService auditService;
    private final TestModeService testModeService;
    private final Map<NodeType, NodeProcessor> nodeProcessors;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final AutomationConstantsCodec constantsCodec;
    private final PendingActionService pendingActionService;
    private final JobRunService jobRunService;
    /** Bounded pool for off-request automation runs (see {@link com.postwerk.config.AsyncExecutionConfig}). */
    private final Executor automationExecutor;
    private final ApplicationEventPublisher eventPublisher;

    /** Job registry ids (admin Background Jobs). */
    public static final String JOB_POLLER = "automation-poller";
    public static final String JOB_DELAYED = "delayed-email";

    /**
     * Self-reference (the transactional proxy) used to dispatch a {@code @Transactional} flow method onto
     * {@link #automationExecutor} so the new thread opens its own transaction. Injected lazily to break the
     * self-referential bean cycle; {@code null} in plain unit tests (then {@link #effectiveSelf()} falls back
     * to {@code this}, which is correct since unit tests run the dispatched task inline with mocked repos).
     */
    private AutomationExecutorService self;

    @Autowired
    public void setSelf(@Lazy AutomationExecutorService self) {
        this.self = self;
    }

    private AutomationExecutorService effectiveSelf() {
        return self != null ? self : this;
    }

    public AutomationExecutorServiceImpl(AutomationRepository automationRepository,
                                          AutomationNodeRepository nodeRepository,
                                          AutomationEdgeRepository edgeRepository,
                                          AutomationExecutionRepository executionRepository,
                                          AutomationDelayedEmailRepository delayedEmailRepository,
                                          EmailRepository emailRepository,
                                          EmailAccountRepository emailAccountRepository,
                                          EmailSyncService emailSyncService,
                                          EmailAutomationTraceService traceService,
                                          EmailAutomationTraceRepository traceRepository,
                                          AuditService auditService,
                                          TestModeService testModeService,
                                          List<NodeProcessor> processors,
                                          ObjectMapper objectMapper,
                                          StringRedisTemplate redisTemplate,
                                          AutomationConstantsCodec constantsCodec,
                                          PendingActionService pendingActionService,
                                          JobRunService jobRunService,
                                          @Qualifier("automationExecutor") Executor automationExecutor,
                                          ApplicationEventPublisher eventPublisher) {
        this.automationRepository = automationRepository;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.executionRepository = executionRepository;
        this.delayedEmailRepository = delayedEmailRepository;
        this.emailRepository = emailRepository;
        this.emailAccountRepository = emailAccountRepository;
        this.emailSyncService = emailSyncService;
        this.traceService = traceService;
        this.traceRepository = traceRepository;
        this.auditService = auditService;
        this.testModeService = testModeService;
        this.nodeProcessors = processors.stream()
                .collect(Collectors.toMap(NodeProcessor::getNodeType, p -> p));
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.constantsCodec = constantsCodec;
        this.pendingActionService = pendingActionService;
        this.jobRunService = jobRunService;
        this.automationExecutor = automationExecutor;
        this.eventPublisher = eventPublisher;
    }

    private static final String POLL_LOCK_KEY = "postwerk:automation:poll-lock";
    private static final java.time.Duration LOCK_TTL = java.time.Duration.ofMinutes(5);

    /** Lua script that deletes the key only if the stored value matches the caller's lock ID. */
    private static final String RELEASE_LOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    // ─── Scheduled Poll ───────────────────────────────────────────
    // Sync all accounts that have active automations, then process unprocessed emails

    @Scheduled(fixedDelay = 60_000)
    public void pollAndExecute() {
        String lockId = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(POLL_LOCK_KEY, lockId, LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("Skipping poll — another instance holds the lock");
            return;
        }
        try {
            jobRunService.run(JOB_POLLER, this::doPollAndExecute);
        } finally {
            redisTemplate.execute(
                    new org.springframework.data.redis.core.script.DefaultRedisScript<>(RELEASE_LOCK_SCRIPT, Long.class),
                    List.of(POLL_LOCK_KEY), lockId);
        }
    }

    /** Admin "run now" for the automation poller — bypasses the schedule, records a manual run. */
    public void runPollNow(UUID actorUserId) {
        jobRunService.runManual(JOB_POLLER, this::doPollAndExecute, actorUserId);
    }

    private void doPollAndExecute() {
        // Memoize each automation's node/edge graph for the duration of this single-threaded poll cycle,
        // so the same graph is not re-fetched once per email (see loadNodes/loadEdges).
        pollGraphCache.set(new GraphCache());
        try {
            // 1. Find all active/testing automations, collect their account IDs
            Instant cutoff = Instant.now().minusSeconds(60);
            List<Automation> dueAutomations = automationRepository.findDueAutomations(
                    List.of(AutomationStatus.ACTIVE, AutomationStatus.TESTING), cutoff);

            Set<UUID> accountIds = new LinkedHashSet<>();
            for (Automation a : dueAutomations) {
                if (a.getAccountIds() != null) {
                    accountIds.addAll(Arrays.asList(a.getAccountIds()));
                }
            }

            if (accountIds.isEmpty()) return;

            // 2. Batch-fetch accounts and sync each
            List<EmailAccount> accounts = emailAccountRepository.findAllById(accountIds);
            for (EmailAccount account : accounts) {
                try {
                    emailSyncService.sync(account);
                } catch (Exception e) {
                    log.warn("Failed to sync account {}: {}", account.getId(), e.getMessage());
                }
            }

            // 3. Process unprocessed emails for each account
            for (UUID accountId : accountIds) {
                List<Email> unprocessed = emailRepository.findTop100ByEmailAccountIdAndProcessedFalseOrderByReceivedAtDesc(accountId);
                for (Email email : unprocessed) {
                    try {
                        processEmail(email);
                    } catch (Exception e) {
                        log.error("Failed to process email {}: {}", email.getId(), e.getMessage());
                    }
                }
            }

            // 4. Batch-update lastRunAt on due automations
            Instant now = Instant.now();
            dueAutomations.forEach(a -> a.setLastRunAt(now));
            automationRepository.saveAll(dueAutomations);
        } catch (Exception e) {
            log.error("Automation poll-and-execute failed unexpectedly", e);
        } finally {
            pollGraphCache.remove();
        }
    }

    // ─── Scheduled: process delayed emails whose delay has expired ──

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void processDelayedEmails() {
        jobRunService.run(JOB_DELAYED, this::doProcessDelayedEmails);
    }

    /** Admin "run now" for the delayed-email processor — records a manual run in its own transaction. */
    @Transactional
    public void runDelayedNow(UUID actorUserId) {
        jobRunService.runManual(JOB_DELAYED, this::doProcessDelayedEmails, actorUserId);
    }

    private void doProcessDelayedEmails() {
        List<AutomationDelayedEmail> expired = delayedEmailRepository
                .findByProcessedFalseAndDelayedUntilBefore(Instant.now());

        for (AutomationDelayedEmail delayed : expired) {
            try {
                Email email = emailRepository.findById(delayed.getEmailId()).orElse(null);
                if (email == null) {
                    delayed.setProcessed(true);
                    delayedEmailRepository.save(delayed);
                    continue;
                }

                Automation automation = automationRepository.findById(delayed.getAutomationId()).orElse(null);
                if (automation == null) {
                    delayed.setProcessed(true);
                    delayedEmailRepository.save(delayed);
                    continue;
                }

                EmailAccount account = emailAccountRepository.findById(email.getEmailAccountId()).orElse(null);
                if (account == null) {
                    log.warn("Delayed email {}: email account {} no longer exists, skipping",
                            delayed.getId(), email.getEmailAccountId());
                    delayed.setProcessed(true);
                    delayedEmailRepository.save(delayed);
                    continue;
                }

                // Resume flow from the delay node's outgoing edges
                var nodes = nodeRepository.findByAutomationId(automation.getId());
                var edges = edgeRepository.findByAutomationId(automation.getId());
                Map<UUID, List<EdgeInfo>> adjacency = buildAdjacencyMap(edges);
                ExecutionContext context = new ExecutionContext(email, account, false)
                        .withOrganizationId(automation.getOrganizationId());

                AutomationNode delayNode = findNodeById(nodes, delayed.getNodeId());
                if (delayNode != null) {
                    EmailAutomationTrace trace = traceService.startTrace(email, automation, null);
                    List<EdgeInfo> outEdges = adjacency.getOrDefault(delayNode.getId(), List.of());
                    for (EdgeInfo edge : outEdges) {
                        AutomationNode targetNode = findNodeById(nodes, edge.targetId);
                        if (targetNode != null) {
                            processNode(targetNode, context, nodes, adjacency, automation.getUserId(), trace);
                        }
                    }
                    traceService.completeTrace(trace, "SUCCESS", null);
                }

                delayed.setProcessed(true);
                delayedEmailRepository.save(delayed);
            } catch (Exception e) {
                log.error("Failed to process delayed email {}: {}", delayed.getId(), e.getMessage());
            }
        }
    }

    // ─── Public: process a single unprocessed email ───────────────
    // Called from scheduled poll and from EmailService when email is read

    @Override
    @Transactional
    public void processEmail(Email email) {
        if (email.isProcessed()) return;

        UUID accountId = email.getEmailAccountId();
        List<Automation> automations = automationRepository.findProcessableByAccountId(accountId);

        if (automations.isEmpty()) {
            email.setProcessed(true);
            emailRepository.save(email);
            return;
        }

        EmailAccount account = emailAccountRepository.findById(accountId).orElse(null);
        if (account == null) {
            email.setProcessed(true);
            emailRepository.save(email);
            return;
        }

        for (Automation automation : automations) {
            processEmailThroughAutomation(email, account, automation);
        }

        email.setProcessed(true);
        emailRepository.save(email);

        log.debug("Email {} processed through {} automation(s)", email.getId(), automations.size());
    }

    @Override
    @Transactional
    public void processEmailById(UUID emailId) {
        emailRepository.findById(emailId).ifPresent(this::processEmail);
    }

    @Override
    public void scheduleProcessEmail(UUID emailId) {
        dispatchAfterCommit(() -> effectiveSelf().processEmailById(emailId));
    }

    // ─── Process one email through one automation ─────────────────

    private void processEmailThroughAutomation(Email email, EmailAccount account, Automation automation) {
        // Skip if this email was already processed by this automation
        if (traceRepository.existsByEmailIdAndAutomationId(email.getId(), automation.getId())) {
            log.debug("Email {} already processed by automation {}, skipping", email.getId(), automation.getName());
            return;
        }

        if (automation.getStatus() == AutomationStatus.TESTING) {
            processEmailInTestMode(email, account, automation);
            return;
        }

        // Create execution record
        var execution = AutomationExecution.builder()
                .automationId(automation.getId())
                .status(ExecutionStatus.RUNNING)
                .processedCount(0)
                .build();
        execution = executionRepository.save(execution);

        // Start trace
        EmailAutomationTrace trace = traceService.startTrace(email, automation, execution.getId());

        try {
            executeFlow(automation, email, account, false, trace);

            traceService.completeTrace(trace, "SUCCESS", null);
            logAutomationExecuted(automation, email, trace, "SUCCESS");

            execution.setProcessedCount(1);
            execution.setStatus(ExecutionStatus.SUCCESS);
            execution.setCompletedAt(Instant.now());
            executionRepository.save(execution);

            automation.setLastRunAt(Instant.now());
            automationRepository.save(automation);

        } catch (Exception e) {
            traceService.completeTrace(trace, "FAILED", e.getMessage());
            logAutomationExecuted(automation, email, trace, "FAILED");

            execution.setStatus(ExecutionStatus.FAILED);
            execution.setErrorLog(e.getMessage());
            execution.setCompletedAt(Instant.now());
            executionRepository.save(execution);

            log.error("Email {} failed in automation {}: {}", email.getId(), automation.getName(), e.getMessage());

            eventPublisher.publishEvent(new AutomationFailedEvent(
                    automation.getOrganizationId(), automation.getUserId(), automation.getId(),
                    automation.getName(), e.getMessage()));
        }
    }

    private void processEmailInTestMode(Email email, EmailAccount account, Automation automation) {
        EmailAutomationTrace trace = traceService.startTrace(email, automation, null);
        trace.setSimulation(true); // badge the trace as a Simulationsmodus dry-run (persisted on completeTrace save)
        try {
            executeFlow(automation, email, account, true, trace);
            traceService.completeTrace(trace, "SUCCESS", null);
            testModeService.recordTestModeExecution(automation, email, trace);
            automation.setLastRunAt(Instant.now());
            automationRepository.save(automation);
        } catch (Exception e) {
            traceService.completeTrace(trace, "FAILED", e.getMessage());
            log.error("Test mode execution failed for email {} in automation {}: {}",
                    email.getId(), automation.getName(), e.getMessage());
        }
    }

    // ─── Public: run an automation from an inbound webhook trigger ─

    /**
     * Accepts an inbound webhook trigger: validates and records the run synchronously, then executes the
     * node graph OFF the request thread.
     *
     * <p>The public ingress endpoint must not block on (or hold a DB connection across) the automation's
     * downstream I/O — Gemini, outbound HTTP, IMAP/SMTP — otherwise a slow downstream lets an unauthenticated
     * caller exhaust the request-serving connection pool. So this method only persists the synthetic email and
     * a {@code RUNNING} {@link AutomationExecution} synchronously, returns its id immediately (HTTP 202), and
     * defers the actual flow to {@link #runInboundWebhookFlow} on the bounded {@code automationExecutor}
     * (dispatched after this transaction commits, so the flow's own transaction sees the persisted rows).</p>
     *
     * @return the created execution id
     */
    @Override
    @Transactional
    public UUID runInboundWebhook(Automation automation, UUID triggerNodeId,
                                  EmailAccount account, Email syntheticEmail,
                                  Map<String, Object> triggerVars) {
        // Validate the trigger node synchronously so a genuine misconfiguration still surfaces to the caller.
        var nodes = loadNodes(automation.getId());
        AutomationNode triggerNode = findNodeById(nodes, triggerNodeId);
        if (triggerNode == null) {
            throw new IllegalStateException("Trigger node " + triggerNodeId + " not found");
        }

        // Persist the synthetic email so the trace (FK email_id) can be recorded.
        // Account-less webhook runs (API-to-API) carry no email at all.
        Email persistedEmail = (syntheticEmail != null) ? emailRepository.save(syntheticEmail) : null;

        var execution = executionRepository.save(AutomationExecution.builder()
                .automationId(automation.getId())
                .status(ExecutionStatus.RUNNING)
                .processedCount(0)
                .build());

        final AutomationExecution exec = execution;
        final UUID triggerId = triggerNode.getId();
        dispatchAfterCommit(() -> effectiveSelf().runInboundWebhookFlow(
                exec, automation, triggerId, account, persistedEmail, triggerVars));

        return execution.getId();
    }

    /**
     * Runs the inbound-webhook node graph and finalizes the execution/trace. Invoked on the bounded
     * {@code automationExecutor} (in its own transaction via the lazy self-proxy) so the blocking node I/O
     * never runs on the ingress request thread. Public only so the transactional proxy applies.
     */
    @Override
    @Transactional
    public void runInboundWebhookFlow(AutomationExecution execution, Automation automation, UUID triggerNodeId,
                                      EmailAccount account, Email persistedEmail,
                                      Map<String, Object> triggerVars) {
        var nodes = loadNodes(automation.getId());
        var edges = loadEdges(automation.getId());

        AutomationNode triggerNode = findNodeById(nodes, triggerNodeId);
        if (triggerNode == null) {
            log.error("Inbound webhook flow: trigger node {} not found for automation {}", triggerNodeId, automation.getId());
            return;
        }

        EmailAutomationTrace trace = traceService.startTrace(persistedEmail, automation, execution.getId());

        try {
            Map<UUID, List<EdgeInfo>> adjacency = buildAdjacencyMap(edges);
            ExecutionContext context = new ExecutionContext(persistedEmail, account, false)
                    .withOrganizationId(automation.getOrganizationId())
                    .withVariables(buildConstantVars(automation))
                    .withVariables(triggerVars);
            processNode(triggerNode, context, nodes, adjacency, automation.getUserId(), trace);

            traceService.completeTrace(trace, "SUCCESS", null);
            logAutomationExecuted(automation, persistedEmail, trace, "SUCCESS");

            execution.setProcessedCount(1);
            execution.setStatus(ExecutionStatus.SUCCESS);
            execution.setCompletedAt(Instant.now());
            executionRepository.save(execution);

            automation.setLastRunAt(Instant.now());
            automationRepository.save(automation);

        } catch (Exception e) {
            traceService.completeTrace(trace, "FAILED", e.getMessage());
            logAutomationExecuted(automation, persistedEmail, trace, "FAILED");

            execution.setStatus(ExecutionStatus.FAILED);
            execution.setErrorLog(e.getMessage());
            execution.setCompletedAt(Instant.now());
            executionRepository.save(execution);

            log.error("Inbound webhook execution failed for automation {}: {}", automation.getName(), e.getMessage());
        }
    }

    // ─── Per-poll-cycle graph cache + async dispatch helpers ───────

    /** Holds an automation's node/edge graph for the duration of one poll cycle (single thread). */
    private static final class GraphCache {
        final Map<UUID, List<AutomationNode>> nodes = new HashMap<>();
        final Map<UUID, List<AutomationEdge>> edges = new HashMap<>();
    }

    /** Set only on the poll thread (see {@link #doPollAndExecute}); null elsewhere → loads always hit the DB. */
    private final ThreadLocal<GraphCache> pollGraphCache = new ThreadLocal<>();

    private List<AutomationNode> loadNodes(UUID automationId) {
        GraphCache cache = pollGraphCache.get();
        return (cache == null)
                ? nodeRepository.findByAutomationId(automationId)
                : cache.nodes.computeIfAbsent(automationId, nodeRepository::findByAutomationId);
    }

    private List<AutomationEdge> loadEdges(UUID automationId) {
        GraphCache cache = pollGraphCache.get();
        return (cache == null)
                ? edgeRepository.findByAutomationId(automationId)
                : cache.edges.computeIfAbsent(automationId, edgeRepository::findByAutomationId);
    }

    /**
     * Dispatches {@code task} to the bounded {@link #automationExecutor}, deferred until after the current
     * transaction commits (so the task's own transaction sees committed rows). With no active transaction
     * synchronization (e.g. plain unit tests) it runs immediately on the executor.
     */
    private void dispatchAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    automationExecutor.execute(task);
                }
            });
        } else {
            automationExecutor.execute(task);
        }
    }

    // ─── Execute flow (shared between normal and dry-run) ──────────

    EmailAutomationTrace executeFlow(Automation automation, Email email, EmailAccount account,
                                      boolean dryRun, EmailAutomationTrace trace) {
        return executeFlow(automation, email, account, dryRun, trace, Map.of(), Map.of());
    }

    /**
     * Drives an automation's node graph from its entry node. {@code AUTOMATION}-kind flows start at
     * the {@code TRIGGER} node; {@code INTEGRATION}-kind flows start at the single {@code INPUT} node
     * (with an {@link ExecutionContext.OutputSink} attached so an {@code OUTPUT} node can return).
     *
     * @param extraVars pre-namespaced variables seeded into the context (e.g. {@code trigger.*}
     *                  from an inbound payload, {@code input.*} for an integration test)
     * @param mocks     per-node mock registry from a test case (empty for live runs)
     */
    EmailAutomationTrace executeFlow(Automation automation, Email email, EmailAccount account,
                                      boolean dryRun, EmailAutomationTrace trace,
                                      Map<String, Object> extraVars, Map<String, NodeMock> mocks) {
        var nodes = loadNodes(automation.getId());
        var edges = loadEdges(automation.getId());

        boolean isIntegration = automation.getKind() == AutomationKind.INTEGRATION;
        NodeType startType = isIntegration ? NodeType.INPUT : NodeType.TRIGGER;

        AutomationNode startNode = nodes.stream()
                .filter(n -> n.getNodeType() == startType)
                .findFirst()
                .orElse(null);

        if (startNode == null) {
            throw new IllegalStateException("No " + startType + " node found");
        }

        Map<UUID, List<EdgeInfo>> adjacency = buildAdjacencyMap(edges);
        ExecutionContext context = new ExecutionContext(email, account, dryRun)
                .withOrganizationId(automation.getOrganizationId())
                .withVariables(buildConstantVars(automation))
                .withVariables(extraVars != null ? extraVars : Map.of())
                .withMocks(mocks != null ? mocks : Map.of());
        if (isIntegration) {
            context = context.withOutputSink(new ExecutionContext.OutputSink());
        }
        processNode(startNode, context, nodes, adjacency, automation.getUserId(), trace);

        return trace;
    }

    // ─── Integration sub-execution (callable, trigger-less automations) ─────

    /** Maximum integration call nesting depth before aborting to prevent runaway recursion. */
    private static final int MAX_INTEGRATION_DEPTH = 5;

    @Override
    @Transactional
    public Map<String, Object> runIntegration(Automation integration, ExecutionContext callerContext,
                                              Map<String, Object> inputFields, int depth) {
        if (depth > MAX_INTEGRATION_DEPTH) {
            throw new IllegalStateException(
                    "Integration call depth exceeded " + MAX_INTEGRATION_DEPTH + " (possible recursion)");
        }

        var nodes = nodeRepository.findByAutomationId(integration.getId());
        var edges = edgeRepository.findByAutomationId(integration.getId());

        List<AutomationNode> inputNodes = nodes.stream()
                .filter(n -> n.getNodeType() == NodeType.INPUT)
                .toList();
        if (inputNodes.isEmpty()) {
            throw new IllegalStateException("Integration '" + integration.getName() + "' has no INPUT node");
        }
        if (inputNodes.size() > 1) {
            throw new IllegalStateException("Integration '" + integration.getName() + "' has more than one INPUT node");
        }
        AutomationNode inputNode = inputNodes.get(0);

        // Expose caller-supplied fields under the input.* namespace.
        Map<String, Object> inputVars = new LinkedHashMap<>();
        if (inputFields != null) {
            for (Map.Entry<String, Object> e : inputFields.entrySet()) {
                inputVars.put("input." + e.getKey(), e.getValue());
            }
        }

        ExecutionContext.OutputSink sink = new ExecutionContext.OutputSink();
        boolean dryRun = callerContext.isDryRun();
        ExecutionContext context = new ExecutionContext(callerContext.getEmail(), callerContext.getAccount(), dryRun)
                .withOrganizationId(integration.getOrganizationId())
                .withVariables(buildConstantVars(integration))
                .withVariables(inputVars)
                .withOutputSink(sink)
                .withIntegrationDepth(depth);

        Map<UUID, List<EdgeInfo>> adjacency = buildAdjacencyMap(edges);

        // Drive the sub-graph with its own trace: persisted for live runs, in-memory for dry-run.
        EmailAutomationTrace subTrace;
        if (dryRun) {
            subTrace = EmailAutomationTrace.builder()
                    .email(callerContext.getEmail())
                    .automationId(integration.getId())
                    .automationName(integration.getName())
                    .automationColor(integration.getColor())
                    .build();
        } else {
            subTrace = traceService.startTrace(callerContext.getEmail(), integration, null);
        }

        try {
            processNode(inputNode, context, nodes, adjacency, integration.getUserId(), subTrace);
            if (!dryRun) traceService.completeTrace(subTrace, "SUCCESS", null);
        } catch (Exception e) {
            if (!dryRun) traceService.completeTrace(subTrace, "FAILED", e.getMessage());
            throw e;
        }

        return sink.getValues();
    }

    /**
     * Reads the automation's user-defined constants JSON and exposes them as
     * {@code const.NAME} variables for {@code {{const.NAME}}} placeholder resolution.
     */
    private Map<String, Object> buildConstantVars(Automation automation) {
        return constantsCodec.toRuntimeVars(automation.getConstants());
    }

    /**
     * Runs a test in dry-run mode without persisting execution records or updating lastRunAt.
     * Executes the automation's full node graph against a synthetic email, collecting
     * per-node trace results in memory. Action nodes are simulated rather than executed.
     *
     * @param automation    the automation whose node graph will be evaluated
     * @param syntheticEmail the synthetic email constructed from test case input
     * @param account       the email account context (may be a dummy if none configured)
     * @return the completed trace containing all node execution results
     */
    @Override
    public EmailAutomationTrace runTestDryRun(Automation automation, Email syntheticEmail, EmailAccount account) {
        return runTestDryRun(automation, syntheticEmail, account, Map.of(), Map.of(), Map.of());
    }

    /**
     * Runs a test dry-run with full control over the synthetic input. {@code triggerPayload} seeds
     * {@code trigger.*} variables (for WEBHOOK-trigger automations) and {@code inputFields} seed
     * {@code input.*} variables (for {@code INTEGRATION}-kind automations). {@code mocks} substitute
     * per-node responses (webhook/integration/AI) or force the real call (LIVE) instead of the
     * default dry-run simulation.
     */
    @Override
    public EmailAutomationTrace runTestDryRun(Automation automation, Email syntheticEmail, EmailAccount account,
                                              Map<String, NodeMock> mocks,
                                              Map<String, Object> triggerPayload,
                                              Map<String, Object> inputFields) {
        EmailAutomationTrace trace = EmailAutomationTrace.builder()
                .email(syntheticEmail)
                .automationId(automation.getId())
                .automationName(automation.getName())
                .automationColor(automation.getColor())
                .build();

        Map<String, Object> extraVars = new LinkedHashMap<>();
        if (triggerPayload != null) {
            triggerPayload.forEach((k, v) -> extraVars.put("trigger." + k, v));
        }
        if (inputFields != null) {
            inputFields.forEach((k, v) -> extraVars.put("input." + k, v));
        }

        executeFlow(automation, syntheticEmail, account, true, trace, extraVars, mocks);
        return trace;
    }

    // ─── Node trace helper (persisted vs in-memory) ─────────────

    /**
     * Records the result of a single node execution into the trace.
     * In dry-run mode, the trace is stored in memory only (no database persistence).
     * In live mode, delegates to {@code traceService} to persist the node trace record.
     *
     * @param trace        the parent automation trace collecting all node results
     * @param node         the automation node that was executed
     * @param resultStatus the outcome status of the node execution
     * @param resultDetail additional detail map (serialized to JSON)
     * @param dryRun       whether this is a dry-run (in-memory) or live (persisted) execution
     */
    private void recordNodeTrace(EmailAutomationTrace trace, AutomationNode node,
                                  NodeResultStatus resultStatus, Map<String, Object> resultDetail,
                                  boolean dryRun) {
        // Persist node traces whenever the PARENT trace is persisted — this covers live runs AND
        // TESTING/Simulationsmodus runs of real emails (both come from traceService.startTrace, so the
        // trace has a generated id). Only ephemeral, never-saved traces (test-panel dry-runs and
        // integration sub-traces, both built via the builder with id == null) keep node traces in-memory.
        //
        // NOTE: `dryRun` controls SIDE EFFECTS (via the ExecutionContext), NOT trace persistence.
        // Conflating the two previously orphaned in-memory node traces into the persisted parent's
        // cascade=ALL collection (with a null trace_id), which blew up the test-mode transaction at
        // flush so the trace stayed RUNNING and no simulation result was recorded.
        if (trace.getId() != null) {
            traceService.addNodeTrace(trace, node, resultStatus, resultDetail);
        } else {
            // In-memory only — no DB persistence (ephemeral dry-run trace)
            String detailJson;
            try {
                detailJson = objectMapper.writeValueAsString(resultDetail);
            } catch (Exception e) {
                detailJson = "{}";
            }
            EmailNodeTrace nodeTrace = EmailNodeTrace.builder()
                    .id(UUID.randomUUID())
                    .nodeId(node.getId())
                    .nodeType(node.getNodeType())
                    .nodeLabel(node.getLabel())
                    .executionOrder(trace.getNodeTraces().size())
                    .resultStatus(resultStatus)
                    .resultDetail(detailJson)
                    .executedAt(Instant.now())
                    .build();
            trace.getNodeTraces().add(nodeTrace);
        }
    }

    // ─── Supervised mode: execute an approved parked action (#3a) ──

    @Override
    @Transactional
    public boolean executeApprovedAction(PendingAction action) {
        AutomationNode node = nodeRepository.findById(action.getNodeId()).orElse(null);
        if (node == null) {
            log.warn("Approved action {} references a missing node {}", action.getId(), action.getNodeId());
            return false;
        }
        NodeProcessor processor = nodeProcessors.get(node.getNodeType());
        if (processor == null) return false;

        Email email = action.getEmailId() != null ? emailRepository.findById(action.getEmailId()).orElse(null) : null;
        EmailAccount account = (email != null && email.getEmailAccountId() != null)
                ? emailAccountRepository.findById(email.getEmailAccountId()).orElse(null) : null;

        // Restore the variable context captured at park time so the action performs verbatim,
        // scoped to the parked action's owning organization (#4) so org-scoped resources resolve.
        UUID actionOrgId = automationRepository.findById(action.getAutomationId())
                .map(com.postwerk.model.Automation::getOrganizationId).orElse(null);
        ExecutionContext context = new ExecutionContext(email, account, false)
                .withOrganizationId(actionOrgId)
                .withVariables(parseContextSnapshot(action.getContextSnapshot()));

        // Run the node directly (NOT through processNode, so the supervised-mode check is bypassed
        // and the side effect actually performs).
        NodeProcessorResult result = processor.process(node, context, action.getUserId());
        boolean ok = result.status() != NodeResultStatus.ERROR;
        log.info("Executed approved {} action (pending {}) -> {}", node.getNodeType(), action.getId(), result.status());
        return ok;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseContextSnapshot(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse pending-action context snapshot: {}", e.getMessage());
            return Map.of();
        }
    }

    // ─── Node Processing (unchanged logic) ────────────────────────

    private void processNode(AutomationNode node, ExecutionContext context,
                              List<AutomationNode> allNodes, Map<UUID, List<EdgeInfo>> adjacency,
                              UUID userId, EmailAutomationTrace trace) {
        boolean dryRun = context.isDryRun();

        NodeProcessor processor = nodeProcessors.get(node.getNodeType());
        if (processor == null) {
            log.warn("No processor registered for node type: {}", node.getNodeType());
            return;
        }

        if (processor.requiresEmailContext(node) && context.getEmail() == null) {
            Map<String, Object> detail = Map.of(
                    "error", "This step requires an email account, but the automation was triggered without one.",
                    "nodeType", node.getNodeType().name());
            recordNodeTrace(trace, node, NodeResultStatus.ERROR, detail, dryRun);
            return; // halt this branch only; sibling branches continue
        }

        // Supervised mode (#3a): on a LIVE run, an ACTION node may be set to skip or to park its
        // side effect for human approval instead of performing it.
        if (!dryRun && node.getNodeType().isAction()) {
            String mode = readExecutionMode(node);
            if ("OFF".equalsIgnoreCase(mode)) {
                recordNodeTrace(trace, node, NodeResultStatus.SKIPPED,
                        Map.of("reason", "Execution disabled for this node (OFF)"), false);
                return; // halt this branch
            }
            if ("REVIEW".equalsIgnoreCase(mode)) {
                // #3b confidence gating: with a threshold set, auto-execute when the upstream AI
                // classification is confident enough; otherwise park for human approval.
                int threshold = readReviewThreshold(node);
                Double confidence = readUpstreamConfidence(context);
                boolean confident = threshold > 0 && confidence != null && confidence >= threshold;
                if (!confident) {
                    // Resolve the payload without performing the side effect, then park it for approval.
                    NodeProcessorResult resolved = processor.process(node, context.asDryRun(), userId);
                    pendingActionService.park(node, resolved.detail(), context, userId);
                    recordNodeTrace(trace, node, NodeResultStatus.PENDING_APPROVAL, resolved.detail(), false);
                    return; // halt this branch until approved
                }
                // confident enough → fall through and perform the action automatically
            }
        }

        NodeProcessorResult result = processor.process(node, context, userId);
        recordNodeTrace(trace, node, result.status(), result.detail(), dryRun);

        if (result.haltTraversal()) return;

        List<EdgeInfo> outEdges = adjacency.getOrDefault(node.getId(), List.of());

        if (!result.fanOutContexts().isEmpty()) {
            // List fan-out (FOREACH): traverse the body handle once per iteration context, in order.
            for (EdgeInfo edge : outEdges) {
                if (!result.fanOutHandle().equals(edge.sourceHandle)) continue;
                AutomationNode targetNode = findNodeById(allNodes, edge.targetId);
                if (targetNode == null) continue;
                for (ExecutionContext iterationCtx : result.fanOutContexts()) {
                    processNode(targetNode, iterationCtx, allNodes, adjacency, userId, trace);
                }
            }
        } else if (result.followAllEdges()) {
            for (EdgeInfo edge : outEdges) {
                AutomationNode targetNode = findNodeById(allNodes, edge.targetId);
                if (targetNode != null) {
                    processNode(targetNode, context, allNodes, adjacency, userId, trace);
                }
            }
        } else if (!result.contextByHandle().isEmpty()) {
            // Handle-based routing with per-handle context (e.g. EXTRACT)
            for (EdgeInfo edge : outEdges) {
                ExecutionContext handleCtx = result.contextByHandle().get(edge.sourceHandle);
                if (handleCtx != null) {
                    AutomationNode targetNode = findNodeById(allNodes, edge.targetId);
                    if (targetNode != null) {
                        processNode(targetNode, handleCtx, allNodes, adjacency, userId, trace);
                    }
                }
            }
        } else if (!result.activeHandles().isEmpty()) {
            // Handle-based routing with shared context (e.g. FILTER, CATEGORIZE)
            for (EdgeInfo edge : outEdges) {
                if (result.activeHandles().contains(edge.sourceHandle)) {
                    AutomationNode targetNode = findNodeById(allNodes, edge.targetId);
                    if (targetNode != null) {
                        processNode(targetNode, context, allNodes, adjacency, userId, trace);
                    }
                }
            }
        }
    }

    /** Supervised-mode execution mode from a node's config JSON: {@code AUTO} (default), {@code REVIEW}, {@code OFF}. */
    private String readExecutionMode(AutomationNode node) {
        try {
            if (node.getConfig() == null || node.getConfig().isBlank()) return "AUTO";
            JsonNode mode = objectMapper.readTree(node.getConfig()).get("executionMode");
            return mode != null && !mode.isNull() ? mode.asText("AUTO") : "AUTO";
        } catch (Exception e) {
            return "AUTO";
        }
    }

    /** Confidence threshold (0-100) for auto-approving a REVIEW action (#3b); 0 = always review. */
    private int readReviewThreshold(AutomationNode node) {
        try {
            if (node.getConfig() == null || node.getConfig().isBlank()) return 0;
            JsonNode t = objectMapper.readTree(node.getConfig()).get("reviewThreshold");
            return t != null && t.isNumber() ? t.asInt(0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * The AI confidence (0-100) that gates a REVIEW action (#3b): the <b>minimum</b> across all AI
     * decisions on the path to this node — so the weakest classification, not just the latest, drives
     * the gate. {@code null} when no AI decision ran upstream (→ conservatively parked).
     */
    private Double readUpstreamConfidence(ExecutionContext context) {
        return context.getMinConfidence();
    }

    private Map<UUID, List<EdgeInfo>> buildAdjacencyMap(List<AutomationEdge> edges) {
        Map<UUID, List<EdgeInfo>> map = new HashMap<>();
        for (AutomationEdge edge : edges) {
            map.computeIfAbsent(edge.getSourceNode().getId(), k -> new ArrayList<>())
                    .add(new EdgeInfo(edge.getTargetNode().getId(), edge.getSourceHandle(), edge.getTargetHandle()));
        }
        return map;
    }

    private AutomationNode findNodeById(List<AutomationNode> nodes, UUID id) {
        return nodes.stream().filter(n -> n.getId().equals(id)).findFirst().orElse(null);
    }

    private record EdgeInfo(UUID targetId, String sourceHandle, String targetHandle) {}

    private void logAutomationExecuted(Automation automation, Email email, EmailAutomationTrace trace, String status) {
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("automation", automation.getName());
            detail.put("email", email != null && email.getSubject() != null ? email.getSubject() : "");
            detail.put("emailId", email != null ? email.getId().toString() : "");
            detail.put("status", status);

            List<Map<String, String>> nodes = new ArrayList<>();
            if (trace.getNodeTraces() != null) {
                for (EmailNodeTrace nt : trace.getNodeTraces()) {
                    Map<String, String> nodeInfo = new LinkedHashMap<>();
                    nodeInfo.put("type", nt.getNodeType().name());
                    nodeInfo.put("label", nt.getNodeLabel() != null ? nt.getNodeLabel() : nt.getNodeType().name());
                    nodeInfo.put("result", nt.getResultStatus().name());
                    nodes.add(nodeInfo);
                }
            }
            detail.put("nodes", nodes);

            String json = objectMapper.writeValueAsString(detail);
            auditService.log(automation.getUserId(), AuditAction.AUTOMATION_EXECUTED, json, (String) null);
        } catch (Exception e) {
            log.warn("Failed to log automation execution audit: {}", e.getMessage());
        }
    }
}
