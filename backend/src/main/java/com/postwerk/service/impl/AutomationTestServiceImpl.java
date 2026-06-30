package com.postwerk.service.impl;

import com.postwerk.dto.automation.*;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.*;
import com.postwerk.util.RepositoryHelper;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.repository.*;
import com.postwerk.service.AutomationExecutorService;
import com.postwerk.service.AutomationTestService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Implementation of {@link AutomationTestService} that manages automation test cases
 * and orchestrates dry-run test executions. Test cases contain synthetic email inputs
 * and assertions that are evaluated against the automation's node graph output.
 */
@Service
public class AutomationTestServiceImpl implements AutomationTestService {

    private static final Logger log = LoggerFactory.getLogger(AutomationTestServiceImpl.class);
    private static final int MAX_TEST_CASES = 20;

    private final AutomationRepository automationRepository;
    private final AutomationTestCaseRepository testCaseRepository;
    private final AutomationTestResultRepository testResultRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final AutomationExecutorService executorService;
    private final ObjectMapper objectMapper;
    private final AutomationTestServiceImpl self;

    public AutomationTestServiceImpl(AutomationRepository automationRepository,
                                      AutomationTestCaseRepository testCaseRepository,
                                      AutomationTestResultRepository testResultRepository,
                                      EmailAccountRepository emailAccountRepository,
                                      AutomationExecutorService executorService,
                                      ObjectMapper objectMapper,
                                      @Lazy AutomationTestServiceImpl self) {
        this.automationRepository = automationRepository;
        this.testCaseRepository = testCaseRepository;
        this.testResultRepository = testResultRepository;
        this.emailAccountRepository = emailAccountRepository;
        this.executorService = executorService;
        this.objectMapper = objectMapper;
        this.self = self;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AutomationTestCaseResponse> getTestCases(UUID organizationId, UUID automationId) {
        var automation = findAutomation(organizationId, automationId);
        var testCases = testCaseRepository.findByAutomationIdOrderBySortOrder(automation.getId());
        return testCases.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public AutomationTestCaseResponse createTestCase(UUID organizationId, UUID automationId, AutomationTestCaseRequest request) {
        var automation = findAutomation(organizationId, automationId);

        long count = testCaseRepository.countByAutomationId(automation.getId());
        if (count >= MAX_TEST_CASES) {
            throw new IllegalStateException("Maximum of " + MAX_TEST_CASES + " test cases per automation reached");
        }

        try {
            var testCase = AutomationTestCase.builder()
                    .automationId(automation.getId())
                    .name(request.name())
                    .description(request.description())
                    .emailInput(objectMapper.writeValueAsString(request.emailInput()))
                    .assertions(request.assertions() != null ? objectMapper.writeValueAsString(request.assertions()) : null)
                    .mocks(request.mocks() != null ? objectMapper.writeValueAsString(request.mocks()) : null)
                    .sortOrder((int) count)
                    .build();

            testCase = testCaseRepository.save(testCase);
            return toResponse(testCase);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test case", e);
        }
    }

    @Override
    @Transactional
    public AutomationTestCaseResponse updateTestCase(UUID organizationId, UUID automationId, UUID testCaseId, AutomationTestCaseRequest request) {
        findAutomation(organizationId, automationId);
        var testCase = findTestCase(testCaseId, automationId);

        try {
            testCase.setName(request.name());
            testCase.setDescription(request.description());
            testCase.setEmailInput(objectMapper.writeValueAsString(request.emailInput()));
            testCase.setAssertions(request.assertions() != null ? objectMapper.writeValueAsString(request.assertions()) : null);
            testCase.setMocks(request.mocks() != null ? objectMapper.writeValueAsString(request.mocks()) : null);
            testCase = testCaseRepository.save(testCase);
            return toResponse(testCase);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update test case", e);
        }
    }

    @Override
    @Transactional
    public void deleteTestCase(UUID organizationId, UUID automationId, UUID testCaseId) {
        findAutomation(organizationId, automationId);
        var testCase = findTestCase(testCaseId, automationId);

        testResultRepository.deleteByTestCaseId(testCaseId);
        testCaseRepository.delete(testCase);
    }

    @Override
    public AutomationTestResultResponse runTest(UUID organizationId, UUID automationId, UUID testCaseId) {
        var automation = findAutomation(organizationId, automationId);
        var testCase = findTestCase(testCaseId, automationId);

        return self.executeTest(automation, testCase);
    }

    @Override
    public RunAllTestsResponse runAllTests(UUID organizationId, UUID automationId) {
        var automation = findAutomation(organizationId, automationId);
        var testCases = testCaseRepository.findByAutomationIdOrderBySortOrder(automation.getId());

        List<AutomationTestResultResponse> results = new ArrayList<>();
        int passed = 0, failed = 0, errors = 0;

        for (var testCase : testCases) {
            var result = self.executeTest(automation, testCase);
            results.add(result);
            switch (result.status()) {
                case "PASSED" -> passed++;
                case "FAILED" -> failed++;
                case "ERROR" -> errors++;
            }
        }

        return new RunAllTestsResponse(testCases.size(), passed, failed, errors, results);
    }

    // Each invocation runs in its own transaction (called via {@code self} so the proxy applies).
    // The public callers (runTest / runAllTests) are non-transactional, so every executeTest commits
    // independently — a failure in one batch item does not roll back results already persisted by
    // earlier items. Uses the default REQUIRED (not REQUIRES_NEW) so that when a transaction IS
    // already active — e.g. an @Transactional integration test whose parent automation/test-case rows
    // are not yet committed — this joins it and can still see those rows (REQUIRES_NEW would open a
    // separate connection that cannot see the uncommitted parents → FK violation).
    @Transactional
    public AutomationTestResultResponse executeTest(Automation automation, AutomationTestCase testCase) {
        long startTime = System.currentTimeMillis();
        try {
            // Build synthetic email
            TestEmailInput emailInput = objectMapper.readValue(testCase.getEmailInput(), TestEmailInput.class);
            Email syntheticEmail = buildSyntheticEmail(emailInput);

            // Get account for context (use first account from automation or dummy)
            EmailAccount account = null;
            if (automation.getAccountIds() != null && automation.getAccountIds().length > 0) {
                account = emailAccountRepository.findById(automation.getAccountIds()[0]).orElse(null);
            }
            if (account == null) {
                account = new EmailAccount();
                account.setId(UUID.randomUUID());
            }

            // Run dry-run with the test case's mocks and trigger/input payloads
            Map<String, NodeMock> mocks = parseMocks(testCase.getMocks());
            EmailAutomationTrace trace = executorService.runTestDryRun(
                    automation, syntheticEmail, account,
                    mocks, emailInput.triggerPayload(), emailInput.inputFields());
            long durationMs = System.currentTimeMillis() - startTime;

            // Build node results from trace
            List<NodeTraceResult> nodeResults = new ArrayList<>();
            if (trace.getNodeTraces() != null) {
                for (EmailNodeTrace nt : trace.getNodeTraces()) {
                    Map<String, Object> detail = new HashMap<>();
                    if (nt.getResultDetail() != null) {
                        try {
                            detail = objectMapper.readValue(nt.getResultDetail(), new TypeReference<>() {});
                        } catch (Exception e) {
                            log.warn("Failed to parse node trace result detail for node {}: {}", nt.getNodeId(), e.getMessage());
                        }
                    }
                    nodeResults.add(new NodeTraceResult(
                            nt.getNodeId(),
                            nt.getNodeType().name(),
                            nt.getNodeLabel(),
                            nt.getResultStatus().name(),
                            detail
                    ));
                }
            }

            // Evaluate assertions
            List<TestAssertion> assertions = parseAssertions(testCase.getAssertions());
            List<AssertionResult> assertionResults = evaluateAssertions(assertions, nodeResults);

            boolean allPassed = assertionResults.isEmpty() || assertionResults.stream().allMatch(AssertionResult::passed);
            String status = allPassed ? "PASSED" : "FAILED";

            // Persist result
            var result = AutomationTestResult.builder()
                    .testCaseId(testCase.getId())
                    .automationId(automation.getId())
                    .status(status)
                    .nodeResults(objectMapper.writeValueAsString(nodeResults))
                    .assertionResults(objectMapper.writeValueAsString(assertionResults))
                    .durationMs(durationMs)
                    .build();
            result = testResultRepository.save(result);

            return new AutomationTestResultResponse(
                    result.getId(),
                    testCase.getId(),
                    testCase.getName(),
                    status,
                    nodeResults,
                    assertionResults,
                    durationMs,
                    null,
                    result.getExecutedAt()
            );

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("Test execution failed for test case {}: {}", testCase.getId(), e.getMessage());

            try {
                var result = AutomationTestResult.builder()
                        .testCaseId(testCase.getId())
                        .automationId(automation.getId())
                        .status("ERROR")
                        .nodeResults("[]")
                        .durationMs(durationMs)
                        .errorMessage(e.getMessage())
                        .build();
                result = testResultRepository.save(result);

                return new AutomationTestResultResponse(
                        result.getId(),
                        testCase.getId(),
                        testCase.getName(),
                        "ERROR",
                        List.of(),
                        List.of(),
                        durationMs,
                        e.getMessage(),
                        result.getExecutedAt()
                );
            } catch (Exception ex) {
                throw new RuntimeException("Failed to save test result", ex);
            }
        }
    }

    private Email buildSyntheticEmail(TestEmailInput input) {
        // Non-email inputs (WEBHOOK trigger / INTEGRATION input) may leave the email fields null.
        String body = input.body() != null ? input.body() : "";
        var builder = Email.builder()
                .id(UUID.randomUUID())
                .emailAccountId(UUID.randomUUID())
                .messageId("test-" + UUID.randomUUID())
                .folder("INBOX")
                .fromAddress(input.from())
                .fromPersonal(input.from())
                .toAddresses(input.to())
                .subject(input.subject())
                .bodyText(body)
                .bodyHtml("<p>" + body + "</p>")
                .snippet(body.length() > 100 ? body.substring(0, 100) : body)
                .receivedAt(input.receivedAt() != null ? Instant.parse(input.receivedAt()) : Instant.now())
                .isRead(false)
                .isStarred(false)
                .hasAttachments(false)
                .processed(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now());

        if (input.inReplyTo() != null && !input.inReplyTo().isBlank()) {
            builder.inReplyTo(input.inReplyTo());
        }

        // Seed mock attachment metadata so a FOREACH over email.attachments iterates in dry-run.
        if (input.attachments() != null && !input.attachments().isEmpty()) {
            try {
                builder.attachments(objectMapper.writeValueAsString(input.attachments()));
                builder.hasAttachments(true);
            } catch (Exception e) {
                log.warn("Failed to serialize attachments for synthetic email: {}", e.getMessage());
            }
        }

        if (input.categoryIds() != null && !input.categoryIds().isEmpty()) {
            try {
                String categoryJson = objectMapper.writeValueAsString(input.categoryIds());
                builder.categories(categoryJson);
                builder.labels(categoryJson);
            } catch (Exception e) {
                log.warn("Failed to serialize categoryIds for synthetic email: {}", e.getMessage());
            }
        }

        return builder.build();
    }

    private List<TestAssertion> parseAssertions(String assertionsJson) {
        if (assertionsJson == null || assertionsJson.isBlank()) return List.of();
        try {
            return objectMapper.readValue(assertionsJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse assertions JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, NodeMock> parseMocks(String mocksJson) {
        if (mocksJson == null || mocksJson.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(mocksJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse mocks JSON: {}", e.getMessage());
            return Map.of();
        }
    }

    private List<AssertionResult> evaluateAssertions(List<TestAssertion> assertions, List<NodeTraceResult> nodeResults) {
        List<AssertionResult> results = new ArrayList<>();
        for (int i = 0; i < assertions.size(); i++) {
            TestAssertion assertion = assertions.get(i);
            // Find the node trace for this assertion's nodeId
            NodeTraceResult matchingNode = nodeResults.stream()
                    .filter(nr -> nr.nodeId().equals(assertion.nodeId()))
                    .findFirst()
                    .orElse(null);

            if (matchingNode == null) {
                results.add(new AssertionResult(i, false, assertion.expectedStatus(), "NODE_NOT_REACHED"));
                continue;
            }

            // Check status assertion
            boolean statusMatches = assertion.expectedStatus().equals(matchingNode.resultStatus());

            if (assertion.field() != null && !assertion.field().isBlank() && assertion.expectedValue() != null) {
                // Resolve field value with deep lookup for nested structures
                Object actualValue = resolveFieldValue(matchingNode.resultDetail(), assertion.field());
                String actualStr = actualValue != null ? actualValue.toString() : "null";
                boolean fieldMatches = valuesMatch(assertion.expectedValue(), actualStr);
                results.add(new AssertionResult(i, statusMatches && fieldMatches,
                        assertion.expectedStatus() + ":" + assertion.field() + "=" + assertion.expectedValue(),
                        matchingNode.resultStatus() + ":" + assertion.field() + "=" + actualStr));
            } else {
                results.add(new AssertionResult(i, statusMatches, assertion.expectedStatus(), matchingNode.resultStatus()));
            }
        }
        return results;
    }

    /**
     * Resolves a field value from resultDetail, supporting nested maps.
     * First tries direct lookup, then case-insensitive match on top-level keys,
     * then searches nested maps (for EXTRACT's extractedValues → group → field).
     */
    @SuppressWarnings("unchecked")
    private Object resolveFieldValue(Map<String, Object> detail, String field) {
        // 1. Direct lookup (containsKey handles null values)
        if (detail.containsKey(field)) return detail.get(field);

        // 2. Case-insensitive match on top-level keys
        for (Map.Entry<String, Object> entry : detail.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(field)) return entry.getValue();
        }

        // 3. Search inside nested maps (e.g. extractedValues → { group → { field → value } })
        for (Object value : detail.values()) {
            if (value instanceof Map<?, ?> nestedMap) {
                if (nestedMap.containsKey(field)) return ((Map<String, Object>) nestedMap).get(field);
                // Case-insensitive nested lookup
                for (Map.Entry<?, ?> entry : nestedMap.entrySet()) {
                    if (entry.getKey() instanceof String key && key.equalsIgnoreCase(field)) return entry.getValue();
                }
                // 4. One more level deep for extractedValues → group → field
                for (Object innerValue : nestedMap.values()) {
                    if (innerValue instanceof Map<?, ?> innerMap) {
                        if (innerMap.containsKey(field)) return ((Map<String, Object>) innerMap).get(field);
                    }
                }
            }
        }

        log.debug("resolveFieldValue: field '{}' not found in detail keys: {}", field, detail.keySet());
        return null;
    }

    /**
     * Compares expected and actual values. Tries numeric comparison first
     * (so "120.50" matches "120.5"), then falls back to string equality.
     */
    private boolean valuesMatch(String expected, String actual) {
        if (expected.equals(actual)) return true;
        try {
            return new java.math.BigDecimal(expected).compareTo(new java.math.BigDecimal(actual)) == 0;
        } catch (NumberFormatException e) {
            return expected.equalsIgnoreCase(actual);
        }
    }

    private AutomationTestCaseResponse toResponse(AutomationTestCase testCase) {
        TestEmailInput emailInput = null;
        List<TestAssertion> assertions = null;
        try {
            emailInput = objectMapper.readValue(testCase.getEmailInput(), TestEmailInput.class);
            if (testCase.getAssertions() != null) {
                assertions = objectMapper.readValue(testCase.getAssertions(), new TypeReference<>() {});
            }
        } catch (Exception e) {
            log.warn("Failed to deserialize test case data: {}", e.getMessage());
        }

        // Get latest result summary
        TestResultSummary lastResult = null;
        var latestResult = testResultRepository.findTop1ByTestCaseIdOrderByExecutedAtDesc(testCase.getId());
        if (latestResult.isPresent()) {
            var lr = latestResult.get();
            int passedCount = 0, totalCount = 0;
            try {
                List<AssertionResult> assertionResults = objectMapper.readValue(
                        lr.getAssertionResults() != null ? lr.getAssertionResults() : "[]",
                        new TypeReference<>() {});
                totalCount = assertionResults.size();
                passedCount = (int) assertionResults.stream().filter(AssertionResult::passed).count();
            } catch (Exception e) {
                log.warn("Failed to parse assertion results for test result summary: {}", e.getMessage());
            }
            lastResult = new TestResultSummary(
                    lr.getStatus(),
                    passedCount,
                    totalCount,
                    lr.getDurationMs() != null ? lr.getDurationMs() : 0,
                    lr.getExecutedAt()
            );
        }

        return new AutomationTestCaseResponse(
                testCase.getId(),
                testCase.getName(),
                testCase.getDescription(),
                emailInput,
                assertions != null ? assertions : List.of(),
                parseMocks(testCase.getMocks()),
                testCase.getSortOrder(),
                lastResult,
                testCase.getCreatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AutomationTestResultResponse getLatestResult(UUID organizationId, UUID automationId, UUID testCaseId) {
        findAutomation(organizationId, automationId);
        AutomationTestCase testCase = findTestCase(testCaseId, automationId);

        var latestResult = testResultRepository.findTop1ByTestCaseIdOrderByExecutedAtDesc(testCaseId);
        if (latestResult.isEmpty()) {
            return null;
        }

        var lr = latestResult.get();
        List<NodeTraceResult> nodeResults = List.of();
        List<AssertionResult> assertionResults = List.of();
        try {
            nodeResults = objectMapper.readValue(lr.getNodeResults(), new TypeReference<>() {});
            if (lr.getAssertionResults() != null) {
                assertionResults = objectMapper.readValue(lr.getAssertionResults(), new TypeReference<>() {});
            }
        } catch (Exception e) {
            log.warn("Failed to deserialize test result data: {}", e.getMessage());
        }

        return new AutomationTestResultResponse(
                lr.getId(),
                testCaseId,
                testCase.getName(),
                lr.getStatus(),
                nodeResults,
                assertionResults,
                lr.getDurationMs() != null ? lr.getDurationMs() : 0,
                lr.getErrorMessage(),
                lr.getExecutedAt()
        );
    }

    private AutomationTestCase findTestCase(UUID testCaseId, UUID automationId) {
        return testCaseRepository.findById(testCaseId)
                .filter(tc -> tc.getAutomationId().equals(automationId))
                .orElseThrow(() -> new ResourceNotFoundException("TestCase", testCaseId));
    }

    private Automation findAutomation(UUID organizationId, UUID automationId) {
        return RepositoryHelper.findOrThrow(automationRepository::findByIdAndOrganizationId, automationId, organizationId, "Automation");
    }
}
