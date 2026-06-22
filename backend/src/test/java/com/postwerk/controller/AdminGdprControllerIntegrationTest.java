package com.postwerk.controller;

import com.postwerk.BaseIntegrationTest;
import com.postwerk.TestFixtures;
import com.postwerk.dto.auth.AuthResponse;
import com.postwerk.dto.auth.LoginRequest;
import com.postwerk.model.User;
import com.postwerk.model.enums.StaffRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the platform-staff GDPR / Data Requests console: queue CRUD, KPIs, retention
 * posture, footprint detail, the lifecycle mutations and the COMPLIANCE_VIEW/MANAGE RBAC split.
 */
class AdminGdprControllerIntegrationTest extends BaseIntegrationTest {

    private AuthResponse registerStaff(String email, StaffRole role) {
        registerAndLogin(email);
        User u = userRepository.findByEmail(email).orElseThrow();
        u.setStaffRole(role);
        userRepository.saveAndFlush(u);
        return authService.login(new LoginRequest(email, TestFixtures.TEST_PASSWORD), TestFixtures.TEST_IP);
    }

    private String createRequest(String adminToken, String subjectEmail, String type) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "subjectEmail", subjectEmail, "subjectName", "Subject " + subjectEmail,
                "type", type, "channel", "EMAIL", "note", "Art. test request"));
        return mockMvc.perform(post("/api/v1/admin/gdpr/requests")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // ─── Create ──────────────────────────────────────────────────────
    @Test
    void create_returnsRequestWithDerivedDeadline() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("gdpr-admin1@example.com");
        String json = createRequest(admin.accessToken(), "subject1@example.com", "EXPORT");
        mockMvc.perform(get("/api/v1/admin/gdpr/requests")
                        .param("search", "subject1@example.com")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].subjectEmail").value("subject1@example.com"))
                .andExpect(jsonPath("$.content[0].type").value("EXPORT"))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].deadlineAt").exists());
    }

    @Test
    void create_invalidType_returns400() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("gdpr-admin2@example.com");
        String body = objectMapper.writeValueAsString(Map.of(
                "subjectEmail", "x@example.com", "subjectName", "X",
                "type", "NONSENSE", "channel", "EMAIL", "note", ""));
        mockMvc.perform(post("/api/v1/admin/gdpr/requests")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    // ─── Detail (footprint + timeline) ────────────────────────────────
    @Test
    void getRequest_matchedSubject_returnsFootprintAndTimeline() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("gdpr-admin3@example.com");
        registerAndLogin("gdpr-real-subject@example.com"); // a real account → footprint resolves
        String created = createRequest(admin.accessToken(), "gdpr-real-subject@example.com", "ACCESS");
        UUID id = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        mockMvc.perform(get("/api/v1/admin/gdpr/requests/" + id)
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.id").value(id.toString()))
                .andExpect(jsonPath("$.footprint.mailboxes").isNumber())
                .andExpect(jsonPath("$.footprint.auditEntries").isNumber())
                .andExpect(jsonPath("$.timeline", hasSize(greaterThanOrEqualTo(1))));
    }

    // ─── KPIs + retention ─────────────────────────────────────────────
    @Test
    void kpis_returnsShape() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("gdpr-admin4@example.com");
        createRequest(admin.accessToken(), "kpi-subject@example.com", "ERASURE");
        mockMvc.perform(get("/api/v1/admin/gdpr/kpis")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.pending", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.overdue").isNumber())
                .andExpect(jsonPath("$.dueSoon").isNumber());
    }

    @Test
    void retention_returnsPolicyDefaults() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("gdpr-admin5@example.com");
        mockMvc.perform(get("/api/v1/admin/gdpr/retention")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailDays").value(365))
                .andExpect(jsonPath("$.conversationDays").value(90))
                .andExpect(jsonPath("$.ipDays").value(90))
                .andExpect(jsonPath("$.auditDays").value(730));
    }

    // ─── Lifecycle ────────────────────────────────────────────────────
    @Test
    void reject_thenClosed_blocksFurtherAction() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("gdpr-admin6@example.com");
        String created = createRequest(admin.accessToken(), "reject-subject@example.com", "ACCESS");
        UUID id = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        mockMvc.perform(post("/api/v1/admin/gdpr/requests/" + id + "/reject")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Identity could not be verified"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.closedAt").exists());

        // already closed → 409/400
        mockMvc.perform(post("/api/v1/admin/gdpr/requests/" + id + "/complete")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void markComplete_setsCompleted() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("gdpr-admin7@example.com");
        String created = createRequest(admin.accessToken(), "complete-subject@example.com", "RECTIFICATION");
        UUID id = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        mockMvc.perform(post("/api/v1/admin/gdpr/requests/" + id + "/complete")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.closedAt").exists());
    }

    @Test
    void executeErasure_noMatchingAccount_returns400() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("gdpr-admin8@example.com");
        String created = createRequest(admin.accessToken(), "nobody-here@nowhere.test", "ERASURE");
        UUID id = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        mockMvc.perform(post("/api/v1/admin/gdpr/requests/" + id + "/erase")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isBadRequest());
    }

    // ─── RBAC ─────────────────────────────────────────────────────────
    @Test
    void list_withComplianceViewOnly_returns200() throws Exception {
        AuthResponse auditor = registerStaff("gdpr-auditor1@example.com", StaffRole.AUDITOR);
        mockMvc.perform(get("/api/v1/admin/gdpr/requests")
                        .header("Authorization", "Bearer " + auditor.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void create_withoutComplianceManage_returns403() throws Exception {
        AuthResponse auditor = registerStaff("gdpr-auditor2@example.com", StaffRole.AUDITOR);
        String body = objectMapper.writeValueAsString(Map.of(
                "subjectEmail", "blocked@example.com", "subjectName", "Blocked",
                "type", "EXPORT", "channel", "EMAIL", "note", ""));
        mockMvc.perform(post("/api/v1/admin/gdpr/requests")
                        .header("Authorization", "Bearer " + auditor.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void gdprEndpoints_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/gdpr/requests"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requests_asNonStaff_returns403() throws Exception {
        String token = registerAndGetToken("gdpr-nonstaff@example.com");
        mockMvc.perform(get("/api/v1/admin/gdpr/requests")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
