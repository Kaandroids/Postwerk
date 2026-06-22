package com.postwerk.service;

import com.postwerk.dto.automation.NodeMock;
import com.postwerk.model.Automation;
import com.postwerk.model.AutomationExecution;
import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.postwerk.model.EmailAutomationTrace;
import com.postwerk.model.PendingAction;
import com.postwerk.service.executor.ExecutionContext;

import java.util.Map;
import java.util.UUID;

/**
 * Executes automation node graphs against incoming emails and inbound webhook triggers.
 *
 * <p>Abstraction over the scheduled execution engine so that collaborating services
 * (email ingestion, inbound webhooks, test runs) depend on this contract rather than the
 * concrete implementation.</p>
 *
 * @since 1.0
 */
public interface AutomationExecutorService {

    /**
     * Processes a single unprocessed email through all automations bound to its account.
     * No-op if the email is already processed.
     */
    void processEmail(Email email);

    /**
     * Loads the email by id and processes it (see {@link #processEmail(Email)}). Intended for
     * off-request, background dispatch so the triggering request thread is not blocked by the
     * automation's external I/O. No-op if the email no longer exists or is already processed.
     */
    void processEmailById(UUID emailId);

    /**
     * Schedules {@link #processEmailById(UUID)} to run off the calling request thread (on a bounded pool,
     * after the caller's transaction commits if one is active). Returns immediately so a user-facing request
     * (e.g. opening an email) is never blocked by the automation's external I/O.
     */
    void scheduleProcessEmail(UUID emailId);

    /**
     * Executes an automation synchronously in response to an inbound webhook request,
     * starting traversal at the given trigger node with {@code trigger.*} variables injected.
     *
     * @return the created execution id
     */
    UUID runInboundWebhook(Automation automation, UUID triggerNodeId,
                           EmailAccount account, Email syntheticEmail,
                           Map<String, Object> triggerVars);

    /**
     * Internal: runs the inbound-webhook node graph and finalizes the given execution. Dispatched by
     * {@link #runInboundWebhook} onto a bounded background pool via the transactional self-proxy (hence it
     * must live on this interface). Not intended to be called directly by other collaborators.
     */
    void runInboundWebhookFlow(AutomationExecution execution, Automation automation, UUID triggerNodeId,
                               EmailAccount account, Email persistedEmail,
                               Map<String, Object> triggerVars);

    /**
     * Runs an automation in dry-run mode without persisting execution records, collecting
     * per-node trace results in memory. Action nodes are simulated rather than executed.
     *
     * @return the completed trace containing all node execution results
     */
    EmailAutomationTrace runTestDryRun(Automation automation, Email syntheticEmail, EmailAccount account);

    /**
     * Runs a test dry-run with full control over the synthetic input and per-node mocks.
     *
     * @param mocks         per-node mock registry (keyed by node id string); MOCK synthesizes a
     *                      response (optionally forcing the failure branch), LIVE performs the real
     *                      call. Empty/absent entries fall back to default dry-run simulation.
     * @param triggerPayload values seeded under {@code trigger.*} (WEBHOOK-trigger automations)
     * @param inputFields    values seeded under {@code input.*} ({@code INTEGRATION}-kind automations)
     * @return the completed in-memory trace
     */
    EmailAutomationTrace runTestDryRun(Automation automation, Email syntheticEmail, EmailAccount account,
                                       Map<String, NodeMock> mocks,
                                       Map<String, Object> triggerPayload,
                                       Map<String, Object> inputFields);

    /**
     * Runs a trigger-less {@code INTEGRATION}-kind automation as a callable sub-flow. Traversal begins
     * at the integration's single {@code INPUT} node with {@code inputFields} exposed under
     * {@code input.*} and the integration's own constants under {@code const.*}. The integration's
     * {@code OUTPUT} node (0 or 1) captures the return values, which are returned here.
     *
     * <p>Invoked from {@code IntegrationCallNodeExecutor}; the email/account/dry-run flags are
     * inherited from the calling context so action nodes execute (or are simulated) consistently.</p>
     *
     * @param integration the {@code INTEGRATION}-kind automation to run
     * @param callerContext the calling node's context (provides email, account, dry-run flag)
     * @param inputFields  resolved input field values (raw field name → value; prefixed {@code input.*} internally)
     * @param depth        nesting depth of this call (used to enforce the recursion cap)
     * @return the integration's output field values (empty if it has no {@code OUTPUT} node)
     */
    Map<String, Object> runIntegration(Automation integration, ExecutionContext callerContext,
                                       Map<String, Object> inputFields, int depth);

    /**
     * Performs a previously-parked supervised-mode action (#3a) after a user approves it. Re-runs the
     * single action node live with the variable context captured at park time, so the side effect
     * matches exactly what was previewed.
     *
     * @return {@code true} if the action performed without error
     */
    boolean executeApprovedAction(PendingAction action);
}
