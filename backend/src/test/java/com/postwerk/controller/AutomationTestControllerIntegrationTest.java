package com.postwerk.controller;

import com.postwerk.BaseIntegrationTest;
import com.postwerk.TestFixtures;
import com.postwerk.dto.automation.AutomationTestCaseRequest;
import com.postwerk.dto.automation.TestAssertion;
import com.postwerk.dto.automation.TestEmailInput;
import com.postwerk.model.*;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.model.enums.NodeType;
import com.postwerk.model.enums.TraceStatus;
import com.postwerk.repository.AutomationRepository;
import com.postwerk.repository.AutomationTestCaseRepository;
import com.postwerk.service.impl.AutomationExecutorServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AutomationTestControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AutomationRepository automationRepository;

    @Autowired
    private AutomationTestCaseRepository testCaseRepository;

    @Autowired
    private com.postwerk.repository.OrganizationRepository organizationRepository;

    @MockBean
    private AutomationExecutorServiceImpl automationExecutorService;

    private static final String BASE_URL = "/api/v1/automations";

    // ─── Helper Methods ───────────────────────────────────────────────

    private Automation createAndSaveAutomation(UUID userId) {
        Automation automation = TestFixtures.createAutomation(userId);
        automation.setId(null); // let DB generate
        // Persisted directly (bypassing the service) — stamp the user's personal org so the
        // NOT NULL organization_id constraint (#4 V68) is satisfied.
        automation.setOrganizationId(organizationRepository.findByOwnerUserIdAndPersonalTrue(userId)
                .orElseThrow().getId());
        return automationRepository.save(automation);
    }

    private AutomationTestCaseRequest createTestCaseRequest() {
        return new AutomationTestCaseRequest(
                "Test happy path",
                "Verifies classification works correctly",
                new TestEmailInput(
                        "sender@example.com",
                        "recipient@example.com",
                        "Invoice #123",
                        "Please find attached the invoice for order #123.",
                        null, null, null, null, null
                ),
                List.of(new TestAssertion(
                        UUID.randomUUID(),
                        "SUCCESS",
                        "category",
                        "billing"
                )),
                null
        );
    }

    private AutomationTestCaseRequest createTestCaseRequestWithName(String name) {
        return new AutomationTestCaseRequest(
                name,
                "Description for " + name,
                new TestEmailInput(
                        "sender@example.com",
                        "recipient@example.com",
                        "Subject " + name,
                        "Body content for " + name,
                        null, null, null, null, null
                ),
                List.of(),
                null
        );
    }

    private EmailAutomationTrace buildDummyTrace(Automation automation) {
        UUID nodeId = UUID.randomUUID();
        EmailNodeTrace nodeTrace = EmailNodeTrace.builder()
                .id(UUID.randomUUID())
                .nodeId(nodeId)
                .nodeType(NodeType.CATEGORIZE)
                .nodeLabel("Categorize Email")
                .executionOrder(1)
                .resultStatus(NodeResultStatus.PASSED)
                .resultDetail("{\"category\":\"billing\",\"confidence\":0.95}")
                .executedAt(Instant.now())
                .build();

        EmailAutomationTrace trace = EmailAutomationTrace.builder()
                .id(UUID.randomUUID())
                .automationId(automation.getId())
                .automationName(automation.getName())
                .automationColor(automation.getColor())
                .startedAt(Instant.now().minusMillis(100))
                .completedAt(Instant.now())
                .status(TraceStatus.SUCCESS)
                .nodeTraces(List.of(nodeTrace))
                .build();

        nodeTrace.setTrace(trace);
        return trace;
    }

    // ─── GET /{id}/tests — List Test Cases ────────────────────────────

    @Test
    void getTestCases_emptyList_returns200() throws Exception {
        String token = registerAndGetToken("test-list-empty@example.com");
        User user = userRepository.findByEmail("test-list-empty@example.com").orElseThrow();
        Automation automation = createAndSaveAutomation(user.getId());

        mockMvc.perform(get(BASE_URL + "/" + automation.getId() + "/tests")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getTestCases_withData_returnsList() throws Exception {
        String token = registerAndGetToken("test-list-data@example.com");
        User user = userRepository.findByEmail("test-list-data@example.com").orElseThrow();
        Automation automation = createAndSaveAutomation(user.getId());

        // Create a test case via API first
        mockMvc.perform(post(BASE_URL + "/" + automation.getId() + "/tests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTestCaseRequest())))
                .andExpect(status().isCreated());

        mockMvc.perform(get(BASE_URL + "/" + automation.getId() + "/tests")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Test happy path"))
                .andExpect(jsonPath("$[0].emailInput.from").value("sender@example.com"))
                .andExpect(jsonPath("$[0].emailInput.subject").value("Invoice #123"))
                .andExpect(jsonPath("$[0].sortOrder").value(0));
    }

    @Test
    void getTestCases_automationNotFound_returns404() throws Exception {
        String token = registerAndGetToken("test-list-notfound@example.com");

        mockMvc.perform(get(BASE_URL + "/" + UUID.randomUUID() + "/tests")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTestCases_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(BASE_URL + "/" + UUID.randomUUID() + "/tests"))
                .andExpect(status().isUnauthorized());
    }

    // ─── POST /{id}/tests — Create Test Case ─────────────────────────

    @Test
    void createTestCase_success_returns201() throws Exception {
        String token = registerAndGetToken("test-create@example.com");
        User user = userRepository.findByEmail("test-create@example.com").orElseThrow();
        Automation automation = createAndSaveAutomation(user.getId());

        AutomationTestCaseRequest request = createTestCaseRequest();

        mockMvc.perform(post(BASE_URL + "/" + automation.getId() + "/tests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Test happy path"))
                .andExpect(jsonPath("$.description").value("Verifies classification works correctly"))
                .andExpect(jsonPath("$.emailInput.from").value("sender@example.com"))
                .andExpect(jsonPath("$.emailInput.to").value("recipient@example.com"))
                .andExpect(jsonPath("$.emailInput.subject").value("Invoice #123"))
                .andExpect(jsonPath("$.emailInput.body").value("Please find attached the invoice for order #123."))
                .andExpect(jsonPath("$.assertions", hasSize(1)))
                .andExpect(jsonPath("$.assertions[0].expectedStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.sortOrder").value(0))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void createTestCase_validationError_blankName_returns400() throws Exception {
        String token = registerAndGetToken("test-create-val@example.com");
        User user = userRepository.findByEmail("test-create-val@example.com").orElseThrow();
        Automation automation = createAndSaveAutomation(user.getId());

        AutomationTestCaseRequest request = new AutomationTestCaseRequest(
                "", // blank name
                "Description",
                new TestEmailInput("from@test.com", "to@test.com", "Subject", "Body", null, null, null, null, null),
                List.of(),
                null
        );

        mockMvc.perform(post(BASE_URL + "/" + automation.getId() + "/tests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTestCase_validationError_nullEmailInput_returns400() throws Exception {
        String token = registerAndGetToken("test-create-val2@example.com");
        User user = userRepository.findByEmail("test-create-val2@example.com").orElseThrow();
        Automation automation = createAndSaveAutomation(user.getId());

        AutomationTestCaseRequest request = new AutomationTestCaseRequest(
                "Valid Name",
                "Description",
                null, // null emailInput
                List.of(),
                null
        );

        mockMvc.perform(post(BASE_URL + "/" + automation.getId() + "/tests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTestCase_maxLimit_returns400() throws Exception {
        String token = registerAndGetToken("test-create-max@example.com");
        User user = userRepository.findByEmail("test-create-max@example.com").orElseThrow();
        Automation automation = createAndSaveAutomation(user.getId());

        // Create 20 test cases (maximum)
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post(BASE_URL + "/" + automation.getId() + "/tests")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createTestCaseRequestWithName("Test " + i))))
                    .andExpect(status().isCreated());
        }

        // 21st should fail — limit guard throws IllegalStateException → 400 Bad Request
        mockMvc.perform(post(BASE_URL + "/" + automation.getId() + "/tests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTestCaseRequestWithName("Test overflow"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTestCase_automationNotOwned_returns404() throws Exception {
        String token = registerAndGetToken("test-create-notown@example.com");
        // Create automation for a different user
        String otherToken = registerAndGetToken("test-other-user@example.com");
        User otherUser = userRepository.findByEmail("test-other-user@example.com").orElseThrow();
        Automation automation = createAndSaveAutomation(otherUser.getId());

        mockMvc.perform(post(BASE_URL + "/" + automation.getId() + "/tests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTestCaseRequest())))
                .andExpect(status().isNotFound());
    }

    // ─── PUT /{id}/tests/{testId} — Update Test Case ─────────────────

    @Test
    void updateTestCase_success_returns200() throws Exception {
        String token = registerAndGetToken("test-update@example.com");
        User user = userRepository.findByEmail("test-update@example.com").orElseThrow();
        Automation automation = createAndSaveAutomation(user.getId());

        // Create test case
        String createResponse = mockMvc.perform(post(BASE_URL + "/" + automation.getId() + "/tests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTestCaseRequest())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID testCaseId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        // Update test case
        AutomationTestCaseRequest updateRequest = new AutomationTestCaseRequest(
                "Updated name",
                "Updated description",
                new TestEmailInput("new-sender@example.com", "new-recipient@example.com", "Updated Subject", "Updated body", null, null, null, null, null),
                List.of(),
                null
        );

        mockMvc.perform(put(BASE_URL + "/" + automation.getId() + "/tests/" + testCaseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated name"))
                .andExpect(jsonPath("$.description").value("Updated description"))
                .andExpect(jsonPath("$.emailInput.from").value("new-sender@example.com"))
                .andExpect(jsonPath("$.emailInput.subject").value("Updated Subject"));
    }

    @Test
    void updateTestCase_notFound_returns404() throws Exception {
        String token = registerAndGetToken("test-update-nf@example.com");
        User user = userRepository.findByEmail("test-update-nf@example.com").orElseThrow();
        Automation automation = createAndSaveAutomation(user.getId());

        mockMvc.perform(put(BASE_URL + "/" + automation.getId() + "/tests/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTestCaseRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateTestCase_validationError_returns400() throws Exception {
        String token = registerAndGetToken("test-update-val@example.com");
        User user = userRepository.findByEmail("test-update-val@example.com").orElseThrow();
        Automation automation = createAndSaveAutomation(user.getId());

        // Create test case
        String createResponse = mockMvc.perform(post(BASE_URL + "/" + automation.getId() + "/tests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTestCaseRequest())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID testCaseId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        // Update with invalid data (blank name)
        AutomationTestCaseRequest invalidRequest = new AutomationTestCaseRequest(
                "",
                "Description",
                new TestEmailInput("from@test.com", "to@test.com", "Subject", "Body", null, null, null, null, null),
                List.of(),
                null
        );

        mockMvc.perform(put(BASE_URL + "/" + automation.getId() + "/tests/" + testCaseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    // ─── DELETE /{id}/tests/{testId} — Delete Test Case ───────────────

    @Test
    void deleteTestCase_success_returns204() throws Exception {
        String token = registerAndGetToken("test-delete@example.com");
        User user = userRepository.findByEmail("test-delete@example.com").orElseThrow();
        Automation automation = createAndSaveAutomation(user.getId());

        // Create test case
        String createResponse = mockMvc.perform(post(BASE_URL + "/" + automation.getId() + "/tests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTestCaseRequest())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID testCaseId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        // Delete test case
        mockMvc.perform(delete(BASE_URL + "/" + automation.getId() + "/tests/" + testCaseId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify it's gone
        mockMvc.perform(get(BASE_URL + "/" + automation.getId() + "/tests")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void deleteTestCase_notFound_returns404() throws Exception {
        String token = registerAndGetToken("test-delete-nf@example.com");
        User user = userRepository.findByEmail("test-delete-nf@example.com").orElseThrow();
        Automation automation = createAndSaveAutomation(user.getId());

        mockMvc.perform(delete(BASE_URL + "/" + automation.getId() + "/tests/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ─── POST /{id}/tests/{testId}/run — Run Single Test ──────────────

    @Test
    void runTest_success_returns200() throws Exception {
        String token = registerAndGetToken("test-run@example.com");
        User user = userRepository.findByEmail("test-run@example.com").orElseThrow();
        Automation automation = createAndSaveAutomation(user.getId());

        // Mock the executor to return a dummy trace
        when(automationExecutorService.runTestDryRun(any(Automation.class), any(Email.class), any(EmailAccount.class), any(), any(), any()))
                .thenReturn(buildDummyTrace(automation));

        // Create test case
        String createResponse = mockMvc.perform(post(BASE_URL + "/" + automation.getId() + "/tests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTestCaseRequest())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID testCaseId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        // Run the test
        mockMvc.perform(post(BASE_URL + "/" + automation.getId() + "/tests/" + testCaseId + "/run")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.testCaseId").value(testCaseId.toString()))
                .andExpect(jsonPath("$.testCaseName").value("Test happy path"))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.nodeResults").isArray())
                .andExpect(jsonPath("$.assertionResults").isArray())
                .andExpect(jsonPath("$.durationMs").isNumber())
                .andExpect(jsonPath("$.executedAt").exists());
    }

    @Test
    void runTest_testCaseNotFound_returns404() throws Exception {
        String token = registerAndGetToken("test-run-nf@example.com");
        User user = userRepository.findByEmail("test-run-nf@example.com").orElseThrow();
        Automation automation = createAndSaveAutomation(user.getId());

        mockMvc.perform(post(BASE_URL + "/" + automation.getId() + "/tests/" + UUID.randomUUID() + "/run")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void runTest_automationNotFound_returns404() throws Exception {
        String token = registerAndGetToken("test-run-auto-nf@example.com");

        mockMvc.perform(post(BASE_URL + "/" + UUID.randomUUID() + "/tests/" + UUID.randomUUID() + "/run")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ─── POST /{id}/tests/run-all — Run All Tests ─────────────────────

    @Test
    void runAllTests_success_returns200() throws Exception {
        String token = registerAndGetToken("test-runall@example.com");
        User user = userRepository.findByEmail("test-runall@example.com").orElseThrow();
        Automation automation = createAndSaveAutomation(user.getId());

        // Mock the executor
        when(automationExecutorService.runTestDryRun(any(Automation.class), any(Email.class), any(EmailAccount.class), any(), any(), any()))
                .thenReturn(buildDummyTrace(automation));

        // Create 2 test cases
        mockMvc.perform(post(BASE_URL + "/" + automation.getId() + "/tests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTestCaseRequestWithName("Test A"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post(BASE_URL + "/" + automation.getId() + "/tests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTestCaseRequestWithName("Test B"))))
                .andExpect(status().isCreated());

        // Run all tests
        mockMvc.perform(post(BASE_URL + "/" + automation.getId() + "/tests/run-all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTests").value(2))
                .andExpect(jsonPath("$.passed").isNumber())
                .andExpect(jsonPath("$.failed").isNumber())
                .andExpect(jsonPath("$.errors").isNumber())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results", hasSize(2)));
    }

    @Test
    void runAllTests_noTestCases_returnsEmptyResult() throws Exception {
        String token = registerAndGetToken("test-runall-empty@example.com");
        User user = userRepository.findByEmail("test-runall-empty@example.com").orElseThrow();
        Automation automation = createAndSaveAutomation(user.getId());

        mockMvc.perform(post(BASE_URL + "/" + automation.getId() + "/tests/run-all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTests").value(0))
                .andExpect(jsonPath("$.passed").value(0))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.errors").value(0))
                .andExpect(jsonPath("$.results", hasSize(0)));
    }

    @Test
    void runAllTests_automationNotFound_returns404() throws Exception {
        String token = registerAndGetToken("test-runall-nf@example.com");

        mockMvc.perform(post(BASE_URL + "/" + UUID.randomUUID() + "/tests/run-all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ─── Unauthenticated ──────────────────────────────────────────────

    @Test
    void testEndpoints_unauthenticated_returns401() throws Exception {
        UUID randomId = UUID.randomUUID();
        UUID randomTestId = UUID.randomUUID();

        mockMvc.perform(get(BASE_URL + "/" + randomId + "/tests"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(BASE_URL + "/" + randomId + "/tests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put(BASE_URL + "/" + randomId + "/tests/" + randomTestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete(BASE_URL + "/" + randomId + "/tests/" + randomTestId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(BASE_URL + "/" + randomId + "/tests/" + randomTestId + "/run"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(BASE_URL + "/" + randomId + "/tests/run-all"))
                .andExpect(status().isUnauthorized());
    }
}
