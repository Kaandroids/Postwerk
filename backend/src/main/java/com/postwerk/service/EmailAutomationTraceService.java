package com.postwerk.service;

import com.postwerk.dto.EmailAutomationTraceResponse;
import com.postwerk.model.*;
import com.postwerk.model.enums.NodeResultStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service interface for recording and retrieving automation execution traces per email.
 * Captures node-level results and overall execution status for debugging and audit purposes.
 *
 * @since 1.0
 */
public interface EmailAutomationTraceService {

    EmailAutomationTrace startTrace(Email email, Automation automation, UUID executionId);

    EmailNodeTrace addNodeTrace(EmailAutomationTrace trace, AutomationNode node,
                                 NodeResultStatus resultStatus, Map<String, Object> resultDetail);

    void completeTrace(EmailAutomationTrace trace, String status, String errorMessage);

    List<EmailAutomationTraceResponse> getTracesByEmailId(UUID emailId);
}
