package com.postwerk.service.executor;

import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Strategy interface for automation action executors.
 *
 * <p>Each implementation handles a specific action type (REPLY_TEMPLATE, FORWARD, MOVE_FOLDER, TRASH).
 * Executors are discovered via Spring's component scanning and collected into a list by
 * {@code AutomationExecutorServiceImpl} for dynamic dispatch based on {@link #getActionType()}.</p>
 *
 * @since 1.0
 * @see ExecutionContext
 */
public interface ActionExecutor {

    String getActionType();

    void execute(Email email, EmailAccount account, JsonNode config, ExecutionContext context) throws Exception;
}
