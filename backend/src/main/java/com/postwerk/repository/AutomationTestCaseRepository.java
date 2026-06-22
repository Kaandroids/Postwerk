package com.postwerk.repository;

import com.postwerk.model.AutomationTestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AutomationTestCase} entities.
 * Provides queries to list, count, and bulk-delete test cases by automation.
 */
@Repository
public interface AutomationTestCaseRepository extends JpaRepository<AutomationTestCase, UUID> {

    List<AutomationTestCase> findByAutomationIdOrderBySortOrder(UUID automationId);

    long countByAutomationId(UUID automationId);

    void deleteByAutomationId(UUID automationId);
}
