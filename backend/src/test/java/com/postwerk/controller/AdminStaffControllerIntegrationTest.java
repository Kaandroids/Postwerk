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

/** Integration tests for the platform-staff Staff & Roles console: roster, KPIs, role matrix, grant/change/revoke, RBAC. */
class AdminStaffControllerIntegrationTest extends BaseIntegrationTest {

    private AuthResponse registerStaff(String email, StaffRole role) {
        registerAndLogin(email);
        User u = userRepository.findByEmail(email).orElseThrow();
        u.setStaffRole(role);
        userRepository.saveAndFlush(u);
        return authService.login(new LoginRequest(email, TestFixtures.TEST_PASSWORD), TestFixtures.TEST_IP);
    }
    private UUID userId(String email) { return userRepository.findByEmail(email).orElseThrow().getId(); }

    @Test
    void roster_listsStaffWithSelfFlag() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("staff-admin1@example.com");
        mockMvc.perform(get("/api/v1/admin/staff").param("search", "staff-admin1@example.com")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("staff-admin1@example.com"))
                .andExpect(jsonPath("$.content[0].role").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.content[0].tier").value("PRIVILEGED"))
                .andExpect(jsonPath("$.content[0].self").value(true))
                .andExpect(jsonPath("$.content[0].capabilityCount", greaterThan(10)));
    }

    @Test
    void kpis_returnsShape() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("staff-admin2@example.com");
        mockMvc.perform(get("/api/v1/admin/staff/kpis").header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.superAdmins", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.privileged", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.readOnly").isNumber());
    }

    @Test
    void rolesMatrix_reflectsRealBundles() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("staff-admin3@example.com");
        mockMvc.perform(get("/api/v1/admin/staff/roles").header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasSize(6)))
                .andExpect(jsonPath("$.roles[0].key").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.roles[0].permissions", hasItem("STAFF_MANAGE")))
                .andExpect(jsonPath("$.roles[?(@.key=='ADMIN')].permissions[*]", not(hasItem("STAFF_MANAGE"))))
                .andExpect(jsonPath("$.allPermissions", hasItem("STAFF_MANAGE")));
    }

    @Test
    void candidates_returnsNonStaffUser() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("staff-admin4@example.com");
        registerAndLogin("candidate-user@example.com");
        mockMvc.perform(get("/api/v1/admin/staff/candidates").param("search", "candidate-user")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("candidate-user@example.com"));
    }

    @Test
    void grantChangeRevoke_lifecycle() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("staff-admin5@example.com");
        registerAndLogin("grantee@example.com");
        UUID target = userId("grantee@example.com");

        // grant
        mockMvc.perform(post("/api/v1/admin/staff/" + target)
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("role", "SUPPORT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SUPPORT"))
                .andExpect(jsonPath("$.tier").value("READ_ONLY"))
                .andExpect(jsonPath("$.staffSince").exists());
        // change
        mockMvc.perform(post("/api/v1/admin/staff/" + target)
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("role", "MODERATOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MODERATOR"));
        // revoke
        mockMvc.perform(delete("/api/v1/admin/staff/" + target).header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").doesNotExist());
    }

    @Test
    void setRole_onSelf_returns400() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("staff-admin6@example.com");
        UUID self = userId("staff-admin6@example.com");
        mockMvc.perform(post("/api/v1/admin/staff/" + self)
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("role", "ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    // ─── RBAC ─────────────────────────────────────────────────────────
    @Test
    void roster_withoutStaffManage_returns403() throws Exception {
        // AUDITOR does NOT hold STAFF_MANAGE.
        AuthResponse auditor = registerStaff("staff-auditor@example.com", StaffRole.AUDITOR);
        mockMvc.perform(get("/api/v1/admin/staff").header("Authorization", "Bearer " + auditor.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRole_cannotManageStaff_returns403() throws Exception {
        // ADMIN is the full set MINUS STAFF_MANAGE — must be locked out of this surface.
        AuthResponse adminRole = registerStaff("staff-plainadmin@example.com", StaffRole.ADMIN);
        mockMvc.perform(get("/api/v1/admin/staff").header("Authorization", "Bearer " + adminRole.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffEndpoints_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/staff")).andExpect(status().isUnauthorized());
    }

    @Test
    void roster_asNonStaff_returns403() throws Exception {
        String token = registerAndGetToken("staff-nonstaff@example.com");
        mockMvc.perform(get("/api/v1/admin/staff").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
