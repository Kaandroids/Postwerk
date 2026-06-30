package com.postwerk.service;

import com.postwerk.dto.automation.*;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.*;
import com.postwerk.model.enums.AutomationStatus;
import com.postwerk.model.enums.AutomationType;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.model.enums.NodeType;
import com.postwerk.repository.*;
import com.postwerk.service.impl.AutomationExecutorServiceImpl;
import com.postwerk.service.impl.AutomationTestServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutomationTestServiceTest {

    @Mock private AutomationRepository automationRepository;
    @Mock private AutomationTestCaseRepository testCaseRepository;
    @Mock private AutomationTestResultRepository testResultRepository;
    @Mock private EmailAccountRepository emailAccountRepository;
    @Mock private AutomationExecutorServiceImpl executorService;

    private ObjectMapper objectMapper;
    private AutomationTestServiceImpl service;

    private UUID userId;
    private UUID orgId;
    private UUID automationId;
    private UUID testCaseId;
    private Automation automation;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        // Cannot use @InjectMocks because of @Lazy self-injection; construct manually
        service = new AutomationTestServiceImpl(
                automationRepository,
                testCaseRepository,
                testResultRepository,
                emailAccountRepository,
                executorService,
                objectMapper,
                null // self â€” will be set via reflection
        );
        // Set self field via reflection so self.executeTest() calls work
        Field selfField = AutomationTestServiceImpl.class.getDeclaredField("self");
        selfField.setAccessible(true);
        selfField.set(service, service);

        userId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        automationId = UUID.randomUUID();
        testCaseId = UUID.randomUUID();
        automation = buildAutomation();
    }

    // â”€â”€â”€ getTestCases â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void getTestCases_returnsTestCasesForAutomation() throws Exception {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));

        AutomationTestCase tc1 = buildTestCase("Test 1", 0);
        AutomationTestCase tc2 = buildTestCase("Test 2", 1);
        when(testCaseRepository.findByAutomationIdOrderBySortOrder(automationId)).thenReturn(List.of(tc1, tc2));
        when(testResultRepository.findTop1ByTestCaseIdOrderByExecutedAtDesc(any())).thenReturn(Optional.empty());

        List<AutomationTestCaseResponse> result = service.getTestCases(orgId, automationId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Test 1");
        assertThat(result.get(1).name()).isEqualTo("Test 2");
        verify(automationRepository).findByIdAndOrganizationId(automationId, orgId);
    }

    @Test
    void getTestCases_automationNotFound_throws() {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTestCases(orgId, automationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getTestCases_includesLastResultSummary() throws Exception {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));

        AutomationTestCase tc = buildTestCase("Test with result", 0);
        when(testCaseRepository.findByAutomationIdOrderBySortOrder(automationId)).thenReturn(List.of(tc));

        AutomationTestResult latestResult = AutomationTestResult.builder()
                .id(UUID.randomUUID())
                .testCaseId(tc.getId())
                .automationId(automationId)
                .status("PASSED")
                .nodeResults("[]")
                .assertionResults("[{\"assertionIndex\":0,\"passed\":true,\"expected\":\"PASSED\",\"actual\":\"PASSED\"}]")
                .durationMs(150L)
                .executedAt(Instant.now())
                .build();
        when(testResultRepository.findTop1ByTestCaseIdOrderByExecutedAtDesc(tc.getId()))
                .thenReturn(Optional.of(latestResult));

        List<AutomationTestCaseResponse> result = service.getTestCases(orgId, automationId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).lastResult()).isNotNull();
        assertThat(result.get(0).lastResult().status()).isEqualTo("PASSED");
        assertThat(result.get(0).lastResult().passedCount()).isEqualTo(1);
        assertThat(result.get(0).lastResult().totalCount()).isEqualTo(1);
    }

    // â”€â”€â”€ createTestCase â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void createTestCase_success_savesAndReturns() throws Exception {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));
        when(testCaseRepository.countByAutomationId(automationId)).thenReturn(3L);
        when(testCaseRepository.save(any(AutomationTestCase.class))).thenAnswer(invocation -> {
            AutomationTestCase saved = invocation.getArgument(0);
            saved.setId(testCaseId);
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });
        when(testResultRepository.findTop1ByTestCaseIdOrderByExecutedAtDesc(any())).thenReturn(Optional.empty());

        AutomationTestCaseRequest request = new AutomationTestCaseRequest(
                "My Test",
                "Test description",
                new TestEmailInput("sender@example.com", "receiver@example.com", "Hello", "Body content", null, null, null, null, null, null),
                List.of(new TestAssertion(UUID.randomUUID(), "PASSED", null, null)),
                null
        );

        AutomationTestCaseResponse response = service.createTestCase(orgId, automationId, request);

        assertThat(response.id()).isEqualTo(testCaseId);
        assertThat(response.name()).isEqualTo("My Test");
        assertThat(response.description()).isEqualTo("Test description");
        assertThat(response.emailInput().from()).isEqualTo("sender@example.com");
        assertThat(response.sortOrder()).isEqualTo(3);
        verify(testCaseRepository).save(any(AutomationTestCase.class));
    }

    @Test
    void createTestCase_maxLimitReached_throwsIllegalState() {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));
        when(testCaseRepository.countByAutomationId(automationId)).thenReturn(20L);

        AutomationTestCaseRequest request = new AutomationTestCaseRequest(
                "Overflow Test",
                null,
                new TestEmailInput("a@b.com", "c@d.com", "Sub", "Body", null, null, null, null, null, null),
                null,
                null
        );

        assertThatThrownBy(() -> service.createTestCase(orgId, automationId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Maximum of 20 test cases");

        verify(testCaseRepository, never()).save(any());
    }

    @Test
    void createTestCase_automationNotFound_throws() {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.empty());

        AutomationTestCaseRequest request = new AutomationTestCaseRequest(
                "Test", null,
                new TestEmailInput("a@b.com", "c@d.com", "Sub", "Body", null, null, null, null, null, null),
                null,
                null
        );

        assertThatThrownBy(() -> service.createTestCase(orgId, automationId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // â”€â”€â”€ updateTestCase â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void updateTestCase_success_updatesAndReturns() throws Exception {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));

        AutomationTestCase existing = buildTestCase("Old Name", 0);
        when(testCaseRepository.findById(testCaseId)).thenReturn(Optional.of(existing));
        when(testCaseRepository.save(any(AutomationTestCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(testResultRepository.findTop1ByTestCaseIdOrderByExecutedAtDesc(any())).thenReturn(Optional.empty());

        AutomationTestCaseRequest request = new AutomationTestCaseRequest(
                "Updated Name",
                "Updated desc",
                new TestEmailInput("new@sender.com", "new@receiver.com", "New Subject", "New Body", null, null, null, null, null, null),
                null,
                null
        );

        AutomationTestCaseResponse response = service.updateTestCase(orgId, automationId, testCaseId, request);

        assertThat(response.name()).isEqualTo("Updated Name");
        assertThat(response.description()).isEqualTo("Updated desc");
        assertThat(response.emailInput().from()).isEqualTo("new@sender.com");
        verify(testCaseRepository).save(any(AutomationTestCase.class));
    }

    @Test
    void updateTestCase_testCaseNotFound_throws() {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));
        when(testCaseRepository.findById(testCaseId)).thenReturn(Optional.empty());

        AutomationTestCaseRequest request = new AutomationTestCaseRequest(
                "X", null,
                new TestEmailInput("a@b.com", "c@d.com", "S", "B", null, null, null, null, null, null),
                null,
                null
        );

        assertThatThrownBy(() -> service.updateTestCase(orgId, automationId, testCaseId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateTestCase_testCaseBelongsToDifferentAutomation_throws() {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));

        AutomationTestCase wrongAutomation = buildTestCase("Wrong", 0);
        wrongAutomation.setAutomationId(UUID.randomUUID()); // different automation
        when(testCaseRepository.findById(testCaseId)).thenReturn(Optional.of(wrongAutomation));

        AutomationTestCaseRequest request = new AutomationTestCaseRequest(
                "X", null,
                new TestEmailInput("a@b.com", "c@d.com", "S", "B", null, null, null, null, null, null),
                null,
                null
        );

        assertThatThrownBy(() -> service.updateTestCase(orgId, automationId, testCaseId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // â”€â”€â”€ deleteTestCase â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void deleteTestCase_success_deletesResultsAndTestCase() {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));

        AutomationTestCase existing = buildTestCase("To Delete", 0);
        when(testCaseRepository.findById(testCaseId)).thenReturn(Optional.of(existing));

        service.deleteTestCase(orgId, automationId, testCaseId);

        verify(testResultRepository).deleteByTestCaseId(testCaseId);
        verify(testCaseRepository).delete(existing);
    }

    @Test
    void deleteTestCase_testCaseNotFound_throws() {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));
        when(testCaseRepository.findById(testCaseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteTestCase(orgId, automationId, testCaseId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(testResultRepository, never()).deleteByTestCaseId(any());
        verify(testCaseRepository, never()).delete(any());
    }

    @Test
    void deleteTestCase_belongsToDifferentAutomation_throws() {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));

        AutomationTestCase wrongAutomation = buildTestCase("Wrong", 0);
        wrongAutomation.setAutomationId(UUID.randomUUID());
        when(testCaseRepository.findById(testCaseId)).thenReturn(Optional.of(wrongAutomation));

        assertThatThrownBy(() -> service.deleteTestCase(orgId, automationId, testCaseId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(testResultRepository, never()).deleteByTestCaseId(any());
        verify(testCaseRepository, never()).delete(any());
    }

    // â”€â”€â”€ runTest â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void runTest_success_executesAndReturnsPassed() throws Exception {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));

        AutomationTestCase tc = buildTestCase("Run Test", 0);
        when(testCaseRepository.findById(testCaseId)).thenReturn(Optional.of(tc));

        UUID nodeId = UUID.randomUUID();
        EmailAutomationTrace trace = buildTrace(nodeId);
        when(executorService.runTestDryRun(eq(automation), any(Email.class), any(EmailAccount.class), any(), any(), any()))
                .thenReturn(trace);

        when(testResultRepository.save(any(AutomationTestResult.class))).thenAnswer(invocation -> {
            AutomationTestResult saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setExecutedAt(Instant.now());
            return saved;
        });

        // Account lookup
        EmailAccount account = new EmailAccount();
        account.setId(automation.getAccountIds()[0]);
        when(emailAccountRepository.findById(automation.getAccountIds()[0])).thenReturn(Optional.of(account));

        AutomationTestResultResponse response = service.runTest(orgId, automationId, testCaseId);

        assertThat(response.status()).isEqualTo("PASSED");
        assertThat(response.testCaseName()).isEqualTo("Run Test");
        assertThat(response.nodeResults()).hasSize(1);
        assertThat(response.errorMessage()).isNull();
        verify(executorService).runTestDryRun(eq(automation), any(Email.class), any(EmailAccount.class), any(), any(), any());
        verify(testResultRepository).save(any(AutomationTestResult.class));
    }

    @Test
    void runTest_executionThrowsException_returnsError() throws Exception {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));

        AutomationTestCase tc = buildTestCase("Error Test", 0);
        when(testCaseRepository.findById(testCaseId)).thenReturn(Optional.of(tc));

        EmailAccount account = new EmailAccount();
        account.setId(automation.getAccountIds()[0]);
        when(emailAccountRepository.findById(automation.getAccountIds()[0])).thenReturn(Optional.of(account));

        when(executorService.runTestDryRun(eq(automation), any(Email.class), any(EmailAccount.class), any(), any(), any()))
                .thenThrow(new RuntimeException("AI service unavailable"));

        when(testResultRepository.save(any(AutomationTestResult.class))).thenAnswer(invocation -> {
            AutomationTestResult saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setExecutedAt(Instant.now());
            return saved;
        });

        AutomationTestResultResponse response = service.runTest(orgId, automationId, testCaseId);

        assertThat(response.status()).isEqualTo("ERROR");
        assertThat(response.errorMessage()).isEqualTo("AI service unavailable");
        assertThat(response.nodeResults()).isEmpty();
        verify(testResultRepository).save(any(AutomationTestResult.class));
    }

    @Test
    void runTest_testCaseNotFound_throws() {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));
        when(testCaseRepository.findById(testCaseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.runTest(orgId, automationId, testCaseId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void runTest_testCaseBelongsToDifferentAutomation_throws() {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));

        AutomationTestCase wrongAutomation = buildTestCase("Wrong", 0);
        wrongAutomation.setAutomationId(UUID.randomUUID());
        when(testCaseRepository.findById(testCaseId)).thenReturn(Optional.of(wrongAutomation));

        assertThatThrownBy(() -> service.runTest(orgId, automationId, testCaseId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void runTest_noAccountIds_usesDummyAccount() throws Exception {
        automation.setAccountIds(null);
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));

        AutomationTestCase tc = buildTestCase("No Account Test", 0);
        when(testCaseRepository.findById(testCaseId)).thenReturn(Optional.of(tc));

        UUID nodeId = UUID.randomUUID();
        EmailAutomationTrace trace = buildTrace(nodeId);
        when(executorService.runTestDryRun(eq(automation), any(Email.class), any(EmailAccount.class), any(), any(), any()))
                .thenReturn(trace);

        when(testResultRepository.save(any(AutomationTestResult.class))).thenAnswer(invocation -> {
            AutomationTestResult saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setExecutedAt(Instant.now());
            return saved;
        });

        AutomationTestResultResponse response = service.runTest(orgId, automationId, testCaseId);

        assertThat(response.status()).isEqualTo("PASSED");
        verify(emailAccountRepository, never()).findById(any());
    }

    // â”€â”€â”€ runAllTests â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void runAllTests_allPassed_returnsCorrectCounts() throws Exception {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));

        AutomationTestCase tc1 = buildTestCase("Test 1", 0);
        AutomationTestCase tc2 = buildTestCase("Test 2", 1);
        when(testCaseRepository.findByAutomationIdOrderBySortOrder(automationId)).thenReturn(List.of(tc1, tc2));

        UUID nodeId = UUID.randomUUID();
        EmailAutomationTrace trace = buildTrace(nodeId);
        when(executorService.runTestDryRun(eq(automation), any(Email.class), any(EmailAccount.class), any(), any(), any()))
                .thenReturn(trace);

        EmailAccount account = new EmailAccount();
        account.setId(automation.getAccountIds()[0]);
        when(emailAccountRepository.findById(automation.getAccountIds()[0])).thenReturn(Optional.of(account));

        when(testResultRepository.save(any(AutomationTestResult.class))).thenAnswer(invocation -> {
            AutomationTestResult saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setExecutedAt(Instant.now());
            return saved;
        });

        RunAllTestsResponse response = service.runAllTests(orgId, automationId);

        assertThat(response.totalTests()).isEqualTo(2);
        assertThat(response.passed()).isEqualTo(2);
        assertThat(response.failed()).isEqualTo(0);
        assertThat(response.errors()).isEqualTo(0);
        assertThat(response.results()).hasSize(2);
    }

    @Test
    void runAllTests_mixedResults_countsCorrectly() throws Exception {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));

        AutomationTestCase tc1 = buildTestCase("Pass Test", 0);
        AutomationTestCase tc2 = buildTestCase("Fail Test", 1);
        AutomationTestCase tc3 = buildTestCase("Error Test", 2);
        when(testCaseRepository.findByAutomationIdOrderBySortOrder(automationId)).thenReturn(List.of(tc1, tc2, tc3));

        EmailAccount account = new EmailAccount();
        account.setId(automation.getAccountIds()[0]);
        when(emailAccountRepository.findById(automation.getAccountIds()[0])).thenReturn(Optional.of(account));

        UUID nodeId = UUID.randomUUID();
        EmailAutomationTrace passTrace = buildTrace(nodeId);

        // tc1 passes, tc2 passes (we'll make assertion fail via assertion content), tc3 throws
        when(executorService.runTestDryRun(eq(automation), any(Email.class), any(EmailAccount.class), any(), any(), any()))
                .thenReturn(passTrace)
                .thenReturn(passTrace)
                .thenThrow(new RuntimeException("Boom"));

        when(testResultRepository.save(any(AutomationTestResult.class))).thenAnswer(invocation -> {
            AutomationTestResult saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setExecutedAt(Instant.now());
            return saved;
        });

        RunAllTestsResponse response = service.runAllTests(orgId, automationId);

        assertThat(response.totalTests()).isEqualTo(3);
        assertThat(response.passed()).isEqualTo(2);
        assertThat(response.failed()).isEqualTo(0);
        assertThat(response.errors()).isEqualTo(1);
        assertThat(response.results()).hasSize(3);
        assertThat(response.results().get(2).status()).isEqualTo("ERROR");
        assertThat(response.results().get(2).errorMessage()).isEqualTo("Boom");
    }

    @Test
    void runAllTests_noTestCases_returnsZeroCounts() {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));
        when(testCaseRepository.findByAutomationIdOrderBySortOrder(automationId)).thenReturn(List.of());

        RunAllTestsResponse response = service.runAllTests(orgId, automationId);

        assertThat(response.totalTests()).isEqualTo(0);
        assertThat(response.passed()).isEqualTo(0);
        assertThat(response.failed()).isEqualTo(0);
        assertThat(response.errors()).isEqualTo(0);
        assertThat(response.results()).isEmpty();
    }

    // â”€â”€â”€ Assertion evaluation â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void runTest_withFailedAssertion_returnsFailed() throws Exception {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));

        UUID nodeId = UUID.randomUUID();
        // Test case expects node to have status "EXTRACTED" but node actually returns "PASSED"
        String emailInputJson = objectMapper.writeValueAsString(
                new TestEmailInput("from@test.com", "to@test.com", "Subject", "Body text", null, null, null, null, null, null));
        String assertionsJson = objectMapper.writeValueAsString(
                List.of(new TestAssertion(nodeId, "EXTRACTED", null, null)));

        AutomationTestCase tc = AutomationTestCase.builder()
                .id(testCaseId)
                .automationId(automationId)
                .name("Assertion Fail Test")
                .description(null)
                .emailInput(emailInputJson)
                .assertions(assertionsJson)
                .sortOrder(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(testCaseRepository.findById(testCaseId)).thenReturn(Optional.of(tc));

        // Trace returns node with status PASSED (not EXTRACTED)
        EmailAutomationTrace trace = buildTrace(nodeId);
        when(executorService.runTestDryRun(eq(automation), any(Email.class), any(EmailAccount.class), any(), any(), any()))
                .thenReturn(trace);

        EmailAccount account = new EmailAccount();
        account.setId(automation.getAccountIds()[0]);
        when(emailAccountRepository.findById(automation.getAccountIds()[0])).thenReturn(Optional.of(account));

        when(testResultRepository.save(any(AutomationTestResult.class))).thenAnswer(invocation -> {
            AutomationTestResult saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setExecutedAt(Instant.now());
            return saved;
        });

        AutomationTestResultResponse response = service.runTest(orgId, automationId, testCaseId);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.assertionResults()).hasSize(1);
        assertThat(response.assertionResults().get(0).passed()).isFalse();
    }

    @Test
    void runTest_seedsMockAttachmentsOntoSyntheticEmail() throws Exception {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));

        UUID nodeId = UUID.randomUUID();
        String emailInputJson = objectMapper.writeValueAsString(
                new TestEmailInput("from@test.com", "to@test.com", "Subj", "Body", null, null, null, null, null,
                        List.of(Map.of("name", "invoice.pdf", "contentType", "application/pdf", "size", 1024))));
        String assertionsJson = objectMapper.writeValueAsString(
                List.of(new TestAssertion(nodeId, "PASSED", null, null)));
        AutomationTestCase tc = AutomationTestCase.builder()
                .id(testCaseId).automationId(automationId).name("Attachments Test")
                .emailInput(emailInputJson).assertions(assertionsJson).sortOrder(0)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(testCaseRepository.findById(testCaseId)).thenReturn(Optional.of(tc));

        EmailAutomationTrace trace = buildTrace(nodeId);
        ArgumentCaptor<Email> emailCaptor = ArgumentCaptor.forClass(Email.class);
        when(executorService.runTestDryRun(eq(automation), emailCaptor.capture(), any(EmailAccount.class), any(), any(), any()))
                .thenReturn(trace);
        EmailAccount account = new EmailAccount();
        account.setId(automation.getAccountIds()[0]);
        when(emailAccountRepository.findById(automation.getAccountIds()[0])).thenReturn(Optional.of(account));
        when(testResultRepository.save(any(AutomationTestResult.class))).thenAnswer(invocation -> {
            AutomationTestResult saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setExecutedAt(Instant.now());
            return saved;
        });

        service.runTest(orgId, automationId, testCaseId);

        Email synthetic = emailCaptor.getValue();
        assertThat(synthetic.isHasAttachments()).isTrue();
        assertThat(synthetic.getAttachments()).contains("invoice.pdf").contains("application/pdf");
    }

    @Test
    void runTest_withMatchingAssertion_returnsPassed() throws Exception {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));

        UUID nodeId = UUID.randomUUID();
        String emailInputJson = objectMapper.writeValueAsString(
                new TestEmailInput("from@test.com", "to@test.com", "Subject", "Body text", null, null, null, null, null, null));
        String assertionsJson = objectMapper.writeValueAsString(
                List.of(new TestAssertion(nodeId, "PASSED", null, null)));

        AutomationTestCase tc = AutomationTestCase.builder()
                .id(testCaseId)
                .automationId(automationId)
                .name("Assertion Pass Test")
                .description(null)
                .emailInput(emailInputJson)
                .assertions(assertionsJson)
                .sortOrder(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(testCaseRepository.findById(testCaseId)).thenReturn(Optional.of(tc));

        EmailAutomationTrace trace = buildTrace(nodeId);
        when(executorService.runTestDryRun(eq(automation), any(Email.class), any(EmailAccount.class), any(), any(), any()))
                .thenReturn(trace);

        EmailAccount account = new EmailAccount();
        account.setId(automation.getAccountIds()[0]);
        when(emailAccountRepository.findById(automation.getAccountIds()[0])).thenReturn(Optional.of(account));

        when(testResultRepository.save(any(AutomationTestResult.class))).thenAnswer(invocation -> {
            AutomationTestResult saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setExecutedAt(Instant.now());
            return saved;
        });

        AutomationTestResultResponse response = service.runTest(orgId, automationId, testCaseId);

        assertThat(response.status()).isEqualTo("PASSED");
        assertThat(response.assertionResults()).hasSize(1);
        assertThat(response.assertionResults().get(0).passed()).isTrue();
    }

    // â”€â”€â”€ inReplyTo & categoryIds on synthetic email â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void runTest_withInReplyTo_syntheticEmailHasInReplyToSet() throws Exception {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));

        String emailInputJson = objectMapper.writeValueAsString(
                new TestEmailInput("from@test.com", "to@test.com", "Re: Hello", "Reply body", null,
                        "<original-msg-123@example.com>", null, null, null, null));
        AutomationTestCase tc = AutomationTestCase.builder()
                .id(testCaseId).automationId(automationId).name("Reply Test")
                .emailInput(emailInputJson).sortOrder(0)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(testCaseRepository.findById(testCaseId)).thenReturn(Optional.of(tc));

        UUID nodeId = UUID.randomUUID();
        EmailAutomationTrace trace = buildTrace(nodeId);
        when(executorService.runTestDryRun(eq(automation), argThat(email ->
                "<original-msg-123@example.com>".equals(email.getInReplyTo())
        ), any(EmailAccount.class), any(), any(), any())).thenReturn(trace);

        EmailAccount account = new EmailAccount();
        account.setId(automation.getAccountIds()[0]);
        when(emailAccountRepository.findById(automation.getAccountIds()[0])).thenReturn(Optional.of(account));
        when(testResultRepository.save(any(AutomationTestResult.class))).thenAnswer(inv -> {
            AutomationTestResult saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID()); saved.setExecutedAt(Instant.now());
            return saved;
        });

        AutomationTestResultResponse response = service.runTest(orgId, automationId, testCaseId);
        assertThat(response.status()).isEqualTo("PASSED");
    }

    @Test
    void runTest_withCategoryIds_syntheticEmailHasCategoriesSet() throws Exception {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));

        UUID catId1 = UUID.randomUUID();
        UUID catId2 = UUID.randomUUID();
        String emailInputJson = objectMapper.writeValueAsString(
                new TestEmailInput("from@test.com", "to@test.com", "Categorized", "Body", null,
                        null, List.of(catId1.toString(), catId2.toString()), null, null, null));
        AutomationTestCase tc = AutomationTestCase.builder()
                .id(testCaseId).automationId(automationId).name("Category Test")
                .emailInput(emailInputJson).sortOrder(0)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(testCaseRepository.findById(testCaseId)).thenReturn(Optional.of(tc));

        UUID nodeId = UUID.randomUUID();
        EmailAutomationTrace trace = buildTrace(nodeId);
        when(executorService.runTestDryRun(eq(automation), argThat(email ->
                email.getCategories() != null && email.getCategories().contains(catId1.toString())
        ), any(EmailAccount.class), any(), any(), any())).thenReturn(trace);

        EmailAccount account = new EmailAccount();
        account.setId(automation.getAccountIds()[0]);
        when(emailAccountRepository.findById(automation.getAccountIds()[0])).thenReturn(Optional.of(account));
        when(testResultRepository.save(any(AutomationTestResult.class))).thenAnswer(inv -> {
            AutomationTestResult saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID()); saved.setExecutedAt(Instant.now());
            return saved;
        });

        AutomationTestResultResponse response = service.runTest(orgId, automationId, testCaseId);
        assertThat(response.status()).isEqualTo("PASSED");
    }

    @Test
    void runTest_withNullInReplyToAndCategoryIds_syntheticEmailHasNulls() throws Exception {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));

        String emailInputJson = objectMapper.writeValueAsString(
                new TestEmailInput("from@test.com", "to@test.com", "Normal", "Body", null, null, null, null, null, null));
        AutomationTestCase tc = AutomationTestCase.builder()
                .id(testCaseId).automationId(automationId).name("Normal Test")
                .emailInput(emailInputJson).sortOrder(0)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(testCaseRepository.findById(testCaseId)).thenReturn(Optional.of(tc));

        UUID nodeId = UUID.randomUUID();
        EmailAutomationTrace trace = buildTrace(nodeId);
        when(executorService.runTestDryRun(eq(automation), argThat(email ->
                email.getInReplyTo() == null && email.getCategories() == null
        ), any(EmailAccount.class), any(), any(), any())).thenReturn(trace);

        EmailAccount account = new EmailAccount();
        account.setId(automation.getAccountIds()[0]);
        when(emailAccountRepository.findById(automation.getAccountIds()[0])).thenReturn(Optional.of(account));
        when(testResultRepository.save(any(AutomationTestResult.class))).thenAnswer(inv -> {
            AutomationTestResult saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID()); saved.setExecutedAt(Instant.now());
            return saved;
        });

        AutomationTestResultResponse response = service.runTest(orgId, automationId, testCaseId);
        assertThat(response.status()).isEqualTo("PASSED");
    }

    // â”€â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private Automation buildAutomation() {
        return Automation.builder()
                .id(automationId)
                .userId(userId)
                .name("Test Automation")
                .description("An automation for testing")
                .type(AutomationType.EMAIL)
                .status(AutomationStatus.ACTIVE)
                .accountIds(new UUID[]{UUID.randomUUID()})
                .color("#6366f1")
                .build();
    }

    private AutomationTestCase buildTestCase(String name, int sortOrder) {
        try {
            String emailInputJson = objectMapper.writeValueAsString(
                    new TestEmailInput("sender@test.com", "receiver@test.com", "Test Subject", "Test body content", null, null, null, null, null, null));
            return AutomationTestCase.builder()
                    .id(testCaseId)
                    .automationId(automationId)
                    .name(name)
                    .description("Description for " + name)
                    .emailInput(emailInputJson)
                    .assertions(null)
                    .sortOrder(sortOrder)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private EmailAutomationTrace buildTrace(UUID nodeId) {
        EmailNodeTrace nodeTrace = EmailNodeTrace.builder()
                .id(UUID.randomUUID())
                .nodeId(nodeId)
                .nodeType(NodeType.FILTER)
                .nodeLabel("Test Filter")
                .executionOrder(0)
                .resultStatus(NodeResultStatus.PASSED)
                .resultDetail("{\"matched\": true}")
                .executedAt(Instant.now())
                .build();

        EmailAutomationTrace trace = EmailAutomationTrace.builder()
                .id(UUID.randomUUID())
                .automationId(automationId)
                .automationName("Test Automation")
                .startedAt(Instant.now())
                .completedAt(Instant.now())
                .nodeTraces(new ArrayList<>(List.of(nodeTrace)))
                .build();

        return trace;
    }
}
