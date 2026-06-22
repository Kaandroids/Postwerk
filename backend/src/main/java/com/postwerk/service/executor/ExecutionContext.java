package com.postwerk.service.executor;

import com.postwerk.dto.automation.NodeMock;
import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable execution context carrying state through an automation workflow.
 *
 * <p>Holds the source email, associated account, and accumulated variables from
 * upstream nodes (email.*, extraction_N.*, http_<key>.*, category.*).
 * Supports a copy-on-write pattern via {@link #withVariable} and {@link #withVariables}
 * to maintain immutability between pipeline stages.</p>
 *
 * @since 1.0
 */
public class ExecutionContext {

    private final Email email;
    private final EmailAccount account;
    private final Map<String, Object> variables;
    private final boolean dryRun;
    /**
     * Optional mutable output sink for integration sub-executions. Shared by reference across all
     * copy-on-write derivations so an {@code OUTPUT} node anywhere in the integration's graph can
     * record the returned values, which the calling primitive reads afterwards. {@code null} for
     * ordinary (top-level) automation runs.
     */
    private final OutputSink outputSink;
    /**
     * Nesting depth of the current integration sub-execution (0 at the top-level automation).
     * Used by {@code IntegrationCallNodeExecutor} to enforce a recursion/depth cap.
     */
    private final int integrationDepth;
    /**
     * Per-node mock registry keyed by node id string, supplied by a test case. Shared by reference
     * across all copy-on-write derivations (read-only config). Empty for ordinary (non-test) runs.
     * A node consults {@link #getMock(UUID)} to decide whether to synthesize a response (MOCK) or
     * perform the real call (LIVE) instead of the default dry-run simulation.
     */
    private final Map<String, NodeMock> mocks;

    /**
     * Owning organization of the running automation (#4). Set once at the start of a run; used by
     * executors to resolve org-scoped resources (templates, sender accounts, called integrations).
     * {@code null} until {@link #withOrganizationId(UUID)} is applied.
     */
    private final UUID organizationId;

    public ExecutionContext(Email email, EmailAccount account) {
        this(email, account, new HashMap<>(), false, null, 0, Map.of(), null);
    }

    public ExecutionContext(Email email, EmailAccount account, boolean dryRun) {
        this(email, account, new HashMap<>(), dryRun, null, 0, Map.of(), null);
    }

    private ExecutionContext(Email email, EmailAccount account, Map<String, Object> variables,
                             boolean dryRun, OutputSink outputSink, int integrationDepth,
                             Map<String, NodeMock> mocks, UUID organizationId) {
        this.email = email;
        this.account = account;
        this.variables = variables;
        this.dryRun = dryRun;
        this.outputSink = outputSink;
        this.integrationDepth = integrationDepth;
        this.mocks = mocks != null ? mocks : Map.of();
        this.organizationId = organizationId;
    }

    public Email getEmail() {
        return email;
    }

    public EmailAccount getAccount() {
        return account;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public Object getVariable(String key) {
        return variables.get(key);
    }

    public Map<String, Object> getVariables() {
        return Collections.unmodifiableMap(variables);
    }

    public Map<String, Object> getVariablesByPrefix(String prefix) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    public ExecutionContext withVariable(String key, Object value) {
        Map<String, Object> newVars = new HashMap<>(this.variables);
        newVars.put(key, value);
        return new ExecutionContext(this.email, this.account, newVars, this.dryRun, this.outputSink, this.integrationDepth, this.mocks, this.organizationId);
    }

    public ExecutionContext withVariables(Map<String, Object> vars) {
        Map<String, Object> newVars = new HashMap<>(this.variables);
        newVars.putAll(vars);
        return new ExecutionContext(this.email, this.account, newVars, this.dryRun, this.outputSink, this.integrationDepth, this.mocks, this.organizationId);
    }

    /** Attaches a fresh mutable {@link OutputSink}; used by the integration sub-execution primitive. */
    public ExecutionContext withOutputSink(OutputSink sink) {
        return new ExecutionContext(this.email, this.account, this.variables, this.dryRun, sink, this.integrationDepth, this.mocks, this.organizationId);
    }

    /** Sets the integration nesting depth; used by the sub-execution primitive. */
    public ExecutionContext withIntegrationDepth(int depth) {
        return new ExecutionContext(this.email, this.account, this.variables, this.dryRun, this.outputSink, depth, this.mocks, this.organizationId);
    }

    /** Attaches a per-node mock registry (keyed by node id string) supplied by a test case. */
    public ExecutionContext withMocks(Map<String, NodeMock> mocks) {
        return new ExecutionContext(this.email, this.account, this.variables, this.dryRun, this.outputSink, this.integrationDepth, mocks, this.organizationId);
    }

    /**
     * Returns a dry-run view of this context (same email/account/variables/mocks), used by supervised
     * mode to let an action executor RESOLVE its payload without performing the side effect.
     */
    public ExecutionContext asDryRun() {
        if (this.dryRun) return this;
        return new ExecutionContext(this.email, this.account, this.variables, true, this.outputSink, this.integrationDepth, this.mocks, this.organizationId);
    }

    /** Sets the owning organization of the running automation (#4); applied once at the start of a run. */
    public ExecutionContext withOrganizationId(UUID organizationId) {
        return new ExecutionContext(this.email, this.account, this.variables, this.dryRun, this.outputSink, this.integrationDepth, this.mocks, organizationId);
    }

    /** The owning organization of the running automation, or {@code null} if not set (legacy/test paths). */
    public UUID getOrganizationId() {
        return organizationId;
    }

    /** Internal accumulator key for the per-path minimum AI confidence (#3b); copy-on-write, branch-correct. */
    private static final String MIN_CONFIDENCE_KEY = "__min_confidence";

    /**
     * Records an AI decision's confidence (0-100), keeping the <b>minimum</b> seen on the path to here,
     * and returns the derived context. Because it rides the copy-on-write variable map, each branch
     * tracks its own minimum independently (#3b: the weakest AI step on the path gates the action).
     */
    public ExecutionContext withRecordedConfidence(double confidence) {
        Object existing = variables.get(MIN_CONFIDENCE_KEY);
        double current = existing instanceof Number n ? n.doubleValue() : Double.MAX_VALUE;
        return withVariable(MIN_CONFIDENCE_KEY, Math.min(current, confidence));
    }

    /** The minimum AI confidence (0-100) recorded on the path to here, or {@code null} if no AI decision ran. */
    public Double getMinConfidence() {
        Object v = variables.get(MIN_CONFIDENCE_KEY);
        return v instanceof Number n ? n.doubleValue() : null;
    }

    /** The mock configured for the given node id, or {@code null} if none (default behavior). */
    public NodeMock getMock(UUID nodeId) {
        if (nodeId == null || mocks.isEmpty()) return null;
        return mocks.get(nodeId.toString());
    }

    /** The integration output sink, or {@code null} for ordinary automation runs. */
    public OutputSink getOutputSink() {
        return outputSink;
    }

    /** Nesting depth of the current integration sub-execution (0 at the top level). */
    public int getIntegrationDepth() {
        return integrationDepth;
    }

    // ─── Legacy bridge methods (for backward compatibility during transition) ───

    public void addExtractedData(String key, Map<String, Object> data) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            variables.put(key + "." + entry.getKey(), entry.getValue());
        }
    }

    public Map<String, Object> getAllExtractedData() {
        // Build a flat map of all extraction/webhook variables for legacy template resolution
        Map<String, Object> merged = new HashMap<>();
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String k = entry.getKey();
            // For extraction variables like "extraction_0.fieldName", expose as just "fieldName"
            if (k.startsWith("extraction_")) {
                int dotIdx = k.indexOf('.');
                if (dotIdx >= 0) {
                    merged.put(k.substring(dotIdx + 1), entry.getValue());
                }
            }
            // For HTTP Request variables like "http_<key>.fieldName", expose as just "fieldName"
            if (k.startsWith("http_")) {
                int dotIdx = k.indexOf('.');
                if (dotIdx >= 0) {
                    merged.put(k.substring(dotIdx + 1), entry.getValue());
                }
            }
        }
        return Collections.unmodifiableMap(merged);
    }

    public ExecutionContext withExtraction(String key, Map<String, Object> data) {
        Map<String, Object> newVars = new HashMap<>(this.variables);
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            newVars.put(key + "." + entry.getKey(), entry.getValue());
        }
        return new ExecutionContext(this.email, this.account, newVars, this.dryRun, this.outputSink, this.integrationDepth, this.mocks, this.organizationId);
    }

    /**
     * Mutable holder for an integration's return values, written by the {@code OUTPUT} node and read
     * by the sub-execution primitive once traversal completes. Shared by reference across all
     * copy-on-write context derivations within a single integration run.
     */
    public static final class OutputSink {
        private final Map<String, Object> values = new LinkedHashMap<>();
        private boolean returned = false;

        /** Records the resolved output field values and marks the integration as having returned. */
        public void capture(Map<String, Object> vals) {
            if (vals != null) values.putAll(vals);
            returned = true;
        }

        /** Whether an {@code OUTPUT} node ran and captured values. */
        public boolean isReturned() {
            return returned;
        }

        /** The captured output field values (empty if no OUTPUT node ran). */
        public Map<String, Object> getValues() {
            return values;
        }
    }
}
