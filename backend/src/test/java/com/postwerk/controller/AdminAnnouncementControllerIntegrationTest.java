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

/** Integration tests for the platform-staff Announcements console: CRUD, lifecycle, KPIs, RBAC. */
class AdminAnnouncementControllerIntegrationTest extends BaseIntegrationTest {

    private AuthResponse registerStaff(String email, StaffRole role) {
        registerAndLogin(email);
        User u = userRepository.findByEmail(email).orElseThrow();
        u.setStaffRole(role);
        userRepository.saveAndFlush(u);
        return authService.login(new LoginRequest(email, TestFixtures.TEST_PASSWORD), TestFixtures.TEST_IP);
    }

    private String createFull(String token) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "titleDe", "Wartung heute", "titleEn", "Maintenance today",
                "bodyDe", "Heute Abend.", "bodyEn", "Tonight.",
                "type", "MAINTENANCE", "placement", "BANNER", "audience", "EVERYONE", "dismissible", false));
        return mockMvc.perform(post("/api/v1/admin/announcements")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void create_returnsDraft() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("ann-admin1@example.com");
        createFull(admin.accessToken());
        mockMvc.perform(get("/api/v1/admin/announcements").param("search", "Maintenance today")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].titleEn").value("Maintenance today"))
                .andExpect(jsonPath("$.content[0].type").value("MAINTENANCE"));
    }

    @Test
    void publish_full_goesLive() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("ann-admin2@example.com");
        UUID id = UUID.fromString(objectMapper.readTree(createFull(admin.accessToken())).get("id").asText());
        mockMvc.perform(post("/api/v1/admin/announcements/" + id + "/publish")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycle").value("PUBLISHED"))
                .andExpect(jsonPath("$.status").value("LIVE"));
    }

    @Test
    void publish_missingTranslation_returns400() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("ann-admin3@example.com");
        String body = objectMapper.writeValueAsString(Map.of(
                "titleDe", "Nur DE", "titleEn", "Only EN title", "bodyDe", "Text", // bodyEn missing
                "type", "INFO", "audience", "EVERYONE"));
        String created = mockMvc.perform(post("/api/v1/admin/announcements")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(created).get("id").asText());
        mockMvc.perform(post("/api/v1/admin/announcements/" + id + "/publish")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void endThenArchive_andDetailHasHistory() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("ann-admin4@example.com");
        UUID id = UUID.fromString(objectMapper.readTree(createFull(admin.accessToken())).get("id").asText());
        mockMvc.perform(post("/api/v1/admin/announcements/" + id + "/publish")
                .header("Authorization", "Bearer " + admin.accessToken())).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/announcements/" + id + "/end")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXPIRED"));
        mockMvc.perform(post("/api/v1/admin/announcements/" + id + "/archive")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        mockMvc.perform(get("/api/v1/admin/announcements/" + id)
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history", hasSize(greaterThanOrEqualTo(3))));
    }

    @Test
    void duplicate_createsDraftCopy() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("ann-admin5@example.com");
        UUID id = UUID.fromString(objectMapper.readTree(createFull(admin.accessToken())).get("id").asText());
        mockMvc.perform(post("/api/v1/admin/announcements/" + id + "/duplicate")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.titleEn", startsWith("Copy of")));
    }

    @Test
    void kpis_returnsShape() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("ann-admin6@example.com");
        createFull(admin.accessToken());
        mockMvc.perform(get("/api/v1/admin/announcements/kpis")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drafts", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.live").isNumber())
                .andExpect(jsonPath("$.scheduled").isNumber());
    }

    // ─── RBAC ─────────────────────────────────────────────────────────
    @Test
    void list_withoutAnnouncementManage_returns403() throws Exception {
        AuthResponse auditor = registerStaff("ann-auditor@example.com", StaffRole.AUDITOR);
        mockMvc.perform(get("/api/v1/admin/announcements")
                        .header("Authorization", "Bearer " + auditor.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void announcementsEndpoints_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/announcements")).andExpect(status().isUnauthorized());
    }

    @Test
    void list_asNonStaff_returns403() throws Exception {
        String token = registerAndGetToken("ann-nonstaff@example.com");
        mockMvc.perform(get("/api/v1/admin/announcements")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
