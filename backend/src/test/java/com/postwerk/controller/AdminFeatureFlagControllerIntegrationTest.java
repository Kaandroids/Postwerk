package com.postwerk.controller;

import com.postwerk.BaseIntegrationTest;
import com.postwerk.TestFixtures;
import com.postwerk.dto.auth.AuthResponse;
import com.postwerk.dto.auth.LoginRequest;
import com.postwerk.model.User;
import com.postwerk.model.enums.StaffRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Integration tests for the platform-staff Feature Flags console: CRUD, lifecycle, KPIs, RBAC. */
class AdminFeatureFlagControllerIntegrationTest extends BaseIntegrationTest {

    private AuthResponse registerStaff(String email, StaffRole role) {
        registerAndLogin(email);
        User u = userRepository.findByEmail(email).orElseThrow();
        u.setStaffRole(role);
        userRepository.saveAndFlush(u);
        return authService.login(new LoginRequest(email, TestFixtures.TEST_PASSWORD), TestFixtures.TEST_IP);
    }

    private String create(String token, String key) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "key", key, "name", "Test " + key, "description", "desc", "kind", "RELEASE"));
        return mockMvc.perform(post("/api/v1/admin/feature-flags")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFF"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void create_returnsOffFlag() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("ff-admin1@example.com");
        create(admin.accessToken(), "test.alpha");
        mockMvc.perform(get("/api/v1/admin/feature-flags").param("search", "test.alpha")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].key").value("test.alpha"))
                .andExpect(jsonPath("$.content[0].status").value("OFF"));
    }

    @Test
    void create_duplicateKey_returns400() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("ff-admin2@example.com");
        create(admin.accessToken(), "test.dup");
        String body = objectMapper.writeValueAsString(Map.of("key", "test.dup", "name", "dup", "kind", "RELEASE"));
        mockMvc.perform(post("/api/v1/admin/feature-flags")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_invalidKeyPattern_returns400() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("ff-admin3@example.com");
        String body = objectMapper.writeValueAsString(Map.of("key", "Bad Key!", "name", "x", "kind", "RELEASE"));
        mockMvc.perform(post("/api/v1/admin/feature-flags")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enableThenDisable_togglesStatus() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("ff-admin4@example.com");
        UUID id = UUID.fromString(objectMapper.readTree(create(admin.accessToken(), "test.toggle")).get("id").asText());
        mockMvc.perform(post("/api/v1/admin/feature-flags/" + id + "/enable")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON"))
                .andExpect(jsonPath("$.rollout").value(100));
        mockMvc.perform(post("/api/v1/admin/feature-flags/" + id + "/disable")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFF"));
    }

    @Test
    void update_withRolloutAndOverrides_returnsRolling() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("ff-admin5@example.com");
        UUID id = UUID.fromString(objectMapper.readTree(create(admin.accessToken(), "test.rollout")).get("id").asText());
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Rolling", "kind", "EXPERIMENT", "enabled", true, "rollout", 35,
                "audience", "EVERYONE",
                "overrides", List.of(Map.of("scope", "Staff", "value", "on"))));
        mockMvc.perform(put("/api/v1/admin/feature-flags/" + id)
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ROLLING"))
                .andExpect(jsonPath("$.rollout").value(35))
                .andExpect(jsonPath("$.overrides[0].scope").value("Staff"))
                .andExpect(jsonPath("$.overrides[0].value").value("on"));
    }

    @Test
    void killThenRestore_andHistory() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("ff-admin6@example.com");
        UUID id = UUID.fromString(objectMapper.readTree(create(admin.accessToken(), "test.kill")).get("id").asText());
        mockMvc.perform(post("/api/v1/admin/feature-flags/" + id + "/kill")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("KILLED"));
        mockMvc.perform(post("/api/v1/admin/feature-flags/" + id + "/restore")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.killed").value(false));
        mockMvc.perform(get("/api/v1/admin/feature-flags/" + id)
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history", hasSize(greaterThanOrEqualTo(3))));
    }

    @Test
    void duplicate_createsCopyKey() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("ff-admin7@example.com");
        UUID id = UUID.fromString(objectMapper.readTree(create(admin.accessToken(), "test.orig")).get("id").asText());
        mockMvc.perform(post("/api/v1/admin/feature-flags/" + id + "/duplicate")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("test.orig-copy"))
                .andExpect(jsonPath("$.status").value("OFF"));
    }

    @Test
    void kpis_returnsShape() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("ff-admin8@example.com");
        create(admin.accessToken(), "test.kpi");
        mockMvc.perform(get("/api/v1/admin/feature-flags/kpis")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.off", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.stale").isNumber());
    }

    // ─── RBAC ─────────────────────────────────────────────────────────
    @Test
    void list_withoutFeatureFlagManage_returns403() throws Exception {
        AuthResponse auditor = registerStaff("ff-auditor@example.com", StaffRole.AUDITOR);
        mockMvc.perform(get("/api/v1/admin/feature-flags")
                        .header("Authorization", "Bearer " + auditor.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void flagEndpoints_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/feature-flags")).andExpect(status().isUnauthorized());
    }

    @Test
    void list_asNonStaff_returns403() throws Exception {
        String token = registerAndGetToken("ff-nonstaff@example.com");
        mockMvc.perform(get("/api/v1/admin/feature-flags")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
