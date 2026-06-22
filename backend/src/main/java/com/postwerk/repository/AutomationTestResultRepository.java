package com.postwerk.repository;

import com.postwerk.model.AutomationTestResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AutomationTestResult} entities.
 * Provides queries to retrieve the latest result for a test case, list results
 * by automation, and delete results when a test case is removed.
 */
@Repository
public interface AutomationTestResultRepository extends JpaRepository<AutomationTestResult, UUID> {

    Optional<AutomationTestResult> findTop1ByTestCaseIdOrderByExecutedAtDesc(UUID testCaseId);

    List<AutomationTestResult> findByAutomationIdOrderByExecutedAtDesc(UUID automationId, Pageable pageable);

    void deleteByTestCaseId(UUID testCaseId);
}
