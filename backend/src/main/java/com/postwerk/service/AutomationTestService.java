package com.postwerk.service;

import com.postwerk.dto.automation.*;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for managing automation test cases and executing test runs.
 * Provides CRUD operations for test cases and the ability to run individual or batch tests
 * against an automation's node graph using synthetic email inputs.
 */
public interface AutomationTestService {

    /**
     * Retrieves all test cases for a given automation, ordered by sort order.
     *
     * @param userId       the ID of the owning user
     * @param automationId the ID of the automation
     * @return list of test case responses including latest result summaries
     */
    List<AutomationTestCaseResponse> getTestCases(UUID organizationId, UUID automationId);

    /**
     * Creates a new test case for the specified automation.
     *
     * @param userId       the ID of the owning user
     * @param automationId the ID of the automation
     * @param request      the test case creation request containing name, email input, and assertions
     * @return the created test case response
     * @throws IllegalStateException if the maximum number of test cases per automation is reached
     */
    AutomationTestCaseResponse createTestCase(UUID organizationId, UUID automationId, AutomationTestCaseRequest request);

    /**
     * Updates an existing test case.
     *
     * @param userId       the ID of the owning user
     * @param automationId the ID of the automation
     * @param testCaseId   the ID of the test case to update
     * @param request      the updated test case data
     * @return the updated test case response
     */
    AutomationTestCaseResponse updateTestCase(UUID organizationId, UUID automationId, UUID testCaseId, AutomationTestCaseRequest request);

    /**
     * Deletes a test case and all associated test results.
     *
     * @param userId       the ID of the owning user
     * @param automationId the ID of the automation
     * @param testCaseId   the ID of the test case to delete
     */
    void deleteTestCase(UUID organizationId, UUID automationId, UUID testCaseId);

    /**
     * Executes a single test case against the automation's node graph in dry-run mode.
     *
     * @param userId       the ID of the owning user
     * @param automationId the ID of the automation
     * @param testCaseId   the ID of the test case to run
     * @return the test result response containing node traces and assertion outcomes
     */
    AutomationTestResultResponse runTest(UUID organizationId, UUID automationId, UUID testCaseId);

    /**
     * Executes all test cases for an automation and returns aggregated results.
     *
     * @param userId       the ID of the owning user
     * @param automationId the ID of the automation
     * @return aggregated response with pass/fail/error counts and individual results
     */
    RunAllTestsResponse runAllTests(UUID organizationId, UUID automationId);

    /**
     * Retrieves the latest test result for a given test case.
     *
     * @param userId       the ID of the owning user
     * @param automationId the ID of the automation
     * @param testCaseId   the ID of the test case
     * @return the latest test result response, or null if no results exist
     */
    AutomationTestResultResponse getLatestResult(UUID organizationId, UUID automationId, UUID testCaseId);
}
