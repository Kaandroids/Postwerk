package com.postwerk.service;

import com.postwerk.dto.TestModeFeedbackRequest;
import com.postwerk.dto.TestModeResultResponse;
import com.postwerk.dto.TestModeStatsResponse;
import com.postwerk.model.Automation;
import com.postwerk.model.Email;
import com.postwerk.model.EmailAutomationTrace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TestModeService {

    void recordTestModeExecution(Automation automation, Email email, EmailAutomationTrace trace);

    Page<TestModeResultResponse> getResults(UUID organizationId, UUID automationId, String feedbackFilter, Pageable pageable);

    TestModeResultResponse submitFeedback(UUID organizationId, UUID automationId, UUID resultId, TestModeFeedbackRequest request);

    TestModeStatsResponse getStats(UUID organizationId, UUID automationId);

    void clearResults(UUID organizationId, UUID automationId);

    /**
     * Runs a single already-synced email through an automation in dry-run (no side effects) and records
     * the simulated actions as a test-mode result. Repeatable: uses an ephemeral trace so it neither
     * persists a trace row nor blocks the email from future live processing.
     */
    TestModeResultResponse simulateEmail(UUID organizationId, UUID automationId, UUID emailId);

    /** Deletes a single test-mode result so it no longer counts toward the accuracy statistics. */
    void deleteResult(UUID organizationId, UUID automationId, UUID resultId);
}
