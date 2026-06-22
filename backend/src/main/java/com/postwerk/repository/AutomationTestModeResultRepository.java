package com.postwerk.repository;

import com.postwerk.model.AutomationTestModeResult;
import com.postwerk.model.enums.TestResultFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AutomationTestModeResultRepository extends JpaRepository<AutomationTestModeResult, UUID> {

    Page<AutomationTestModeResult> findByAutomationIdOrderByCreatedAtDesc(UUID automationId, Pageable pageable);

    Page<AutomationTestModeResult> findByAutomationIdAndFeedbackIsNullOrderByCreatedAtDesc(UUID automationId, Pageable pageable);

    Page<AutomationTestModeResult> findByAutomationIdAndFeedbackOrderByCreatedAtDesc(UUID automationId, TestResultFeedback feedback, Pageable pageable);

    long countByAutomationId(UUID automationId);

    long countByAutomationIdAndFeedback(UUID automationId, TestResultFeedback feedback);

    long countByAutomationIdAndFeedbackIsNull(UUID automationId);

    void deleteByAutomationId(UUID automationId);
}
