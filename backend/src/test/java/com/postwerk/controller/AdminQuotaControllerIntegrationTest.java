package com.postwerk.controller;

import com.postwerk.BaseIntegrationTest;
import com.postwerk.TestFixtures;
import com.postwerk.dto.auth.AuthResponse;
import com.postwerk.dto.auth.LoginRequest;
import com.postwerk.model.Organization;
import com.postwerk.model.User;
import com.postwerk.model.enums.StaffRole;
import com.postwerk.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminQuotaControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private OrganizationRepository organizationRepository;

    /** Registers a user, assigns the given staff role (without ADMIN platform role), and logs in. */
    private AuthResponse registerStaff(String email, StaffRole staffRole) {
        registerAndLogin(email);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setStaffRole(staffRole);
        userRepository.saveAndFlush(user);
        return authService.login(new LoginRequest(email, TestFixtures.TEST_PASSWORD), TestFixtures.TEST_IP);
    }

    private UUID personalOrgId(String email) {
        User u = userRepository.findByEmail(email).orElseThrow();
        return organizationRepository.findByOwnerUserIdAndPersonalTrue(u.getId()).orElseThrow().getId();
    }

    private UUID userId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    // ─── Create ──────────────────────────────────────────────────────

    @Test
    void create_userCreditOverride_returns201() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("quota-admin1@example.com");
        registerAndLogin("quota-target1@example.com");

        String body = objectMapper.writeValueAsString(Map.of(
                "targetType", "USER",
                "targetId", userId("quota-target1@example.com"),
                "kind", "CREDIT",
                "amountCents", 500,
                "reason", "Goodwill credit"));

        mockMvc.perform(post("/api/v1/admin/quota-overrides")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.targetType").value("USER"))
                .andExpect(jsonPath("$.kind").value("CREDIT"))
                .andExpect(jsonPath("$.amountCents").value(500))
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.targetEmailOrSlug").value("quota-target1@example.com"))
                .andExpect(jsonPath("$.grantedByName").exists());
    }

    @Test
    void create_orgCapOverride_returns201_andResolvesBaseCap() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("quota-admin2@example.com");
        registerAndLogin("quota-orgowner@example.com");
        UUID orgId = personalOrgId("quota-orgowner@example.com");

        String body = objectMapper.writeValueAsString(Map.of(
                "targetType", "ORG",
                "targetId", orgId,
                "kind", "CAP",
                "amountCents", 9000,
                "reason", "Raised cap for migration"));

        mockMvc.perform(post("/api/v1/admin/quota-overrides")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetType").value("ORG"))
                .andExpect(jsonPath("$.kind").value("CAP"))
                // CAP replaces the base cap → effective == amount
                .andExpect(jsonPath("$.effectiveCapCents").value(9000))
                .andExpect(jsonPath("$.targetId").value(orgId.toString()));
    }

    @Test
    void create_creditWithoutAmount_returns400() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("quota-admin3@example.com");
        registerAndLogin("quota-target3@example.com");

        String body = objectMapper.writeValueAsString(Map.of(
                "targetType", "USER",
                "targetId", userId("quota-target3@example.com"),
                "kind", "CREDIT",
                "reason", "missing amount"));

        mockMvc.perform(post("/api/v1/admin/quota-overrides")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_unknownUserTarget_returns404() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("quota-admin4@example.com");

        String body = objectMapper.writeValueAsString(Map.of(
                "targetType", "USER",
                "targetId", UUID.randomUUID(),
                "kind", "CREDIT",
                "amountCents", 100,
                "reason", "no such user"));

        mockMvc.perform(post("/api/v1/admin/quota-overrides")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_unlimited_ignoresAmount_returns201_nullEffectiveCap() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("quota-admin5@example.com");
        registerAndLogin("quota-target5@example.com");
        UUID orgId = personalOrgId("quota-target5@example.com");

        String body = objectMapper.writeValueAsString(Map.of(
                "targetType", "ORG",
                "targetId", orgId,
                "kind", "UNLIMITED",
                "reason", "VIP customer"));

        mockMvc.perform(post("/api/v1/admin/quota-overrides")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("UNLIMITED"))
                .andExpect(jsonPath("$.amountCents").doesNotExist())
                // unlimited → effectiveCapCents emitted as null
                .andExpect(jsonPath("$.effectiveCapCents").doesNotExist());
    }

    // ─── List ────────────────────────────────────────────────────────

    @Test
    void list_asAdmin_returnsCreatedRow() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("quota-listadmin@example.com");
        registerAndLogin("quota-listtarget@example.com");

        String body = objectMapper.writeValueAsString(Map.of(
                "targetType", "USER",
                "targetId", userId("quota-listtarget@example.com"),
                "kind", "CREDIT",
                "amountCents", 700,
                "reason", "listed credit"));
        mockMvc.perform(post("/api/v1/admin/quota-overrides")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/admin/quota-overrides")
                        .param("search", "quota-listtarget@example.com")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].targetEmailOrSlug").value("quota-listtarget@example.com"))
                .andExpect(jsonPath("$.content[0].kind").value("CREDIT"));
    }

    @Test
    void list_filterByKindAndTargetType() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("quota-filteradmin@example.com");
        registerAndLogin("quota-filtertarget@example.com");
        UUID orgId = personalOrgId("quota-filtertarget@example.com");

        mockMvc.perform(post("/api/v1/admin/quota-overrides")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetType", "ORG", "targetId", orgId,
                                "kind", "UNLIMITED", "reason", "filter test"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/admin/quota-overrides")
                        .param("targetType", "ORG")
                        .param("kind", "UNLIMITED")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].kind", everyItem(is("UNLIMITED"))))
                .andExpect(jsonPath("$.content[*].targetType", everyItem(is("ORG"))));
    }

    // ─── Update (target locked) ──────────────────────────────────────

    @Test
    void update_changesKindAndAmount_keepsLockedTarget() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("quota-updadmin@example.com");
        registerAndLogin("quota-updtarget@example.com");
        UUID targetId = userId("quota-updtarget@example.com");

        String created = mockMvc.perform(post("/api/v1/admin/quota-overrides")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetType", "USER", "targetId", targetId,
                                "kind", "CREDIT", "amountCents", 200, "reason", "initial"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        // Attempt to change the target too — it must be IGNORED (locked); kind/amount DO change.
        String body = objectMapper.writeValueAsString(Map.of(
                "targetType", "ORG",
                "targetId", UUID.randomUUID(),
                "kind", "CAP",
                "amountCents", 1500,
                "reason", "bumped to a hard cap"));

        mockMvc.perform(put("/api/v1/admin/quota-overrides/" + id)
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("CAP"))
                .andExpect(jsonPath("$.amountCents").value(1500))
                // target stayed locked to the original USER target
                .andExpect(jsonPath("$.targetType").value("USER"))
                .andExpect(jsonPath("$.targetId").value(targetId.toString()));
    }

    // ─── Revoke ──────────────────────────────────────────────────────

    @Test
    void revoke_deletesOverride_returns204() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("quota-revadmin@example.com");
        registerAndLogin("quota-revtarget@example.com");

        String created = mockMvc.perform(post("/api/v1/admin/quota-overrides")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetType", "USER", "targetId", userId("quota-revtarget@example.com"),
                                "kind", "CREDIT", "amountCents", 100, "reason", "to revoke"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        mockMvc.perform(delete("/api/v1/admin/quota-overrides/" + id)
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/v1/admin/quota-overrides/" + id)
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetType", "USER", "targetId", userId("quota-revtarget@example.com"),
                                "kind", "CREDIT", "amountCents", 100, "reason", "gone"))))
                .andExpect(status().isNotFound());
    }

    // ─── KPIs ────────────────────────────────────────────────────────

    @Test
    void kpis_asAdmin_returnsShape() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("quota-kpiadmin@example.com");
        registerAndLogin("quota-kpitarget@example.com");

        // an active credit this month + one expiring in 3 days
        mockMvc.perform(post("/api/v1/admin/quota-overrides")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetType", "USER", "targetId", userId("quota-kpitarget@example.com"),
                                "kind", "CREDIT", "amountCents", 1000, "reason", "kpi credit",
                                "expiresAt", Instant.now().plus(3, ChronoUnit.DAYS).toString()))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/admin/quota-overrides/kpis")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.creditGrantedThisMonthCents", greaterThanOrEqualTo(1000)))
                .andExpect(jsonPath("$.over80Count").isNumber())
                .andExpect(jsonPath("$.expiringIn7Count", greaterThanOrEqualTo(1)));
    }

    // ─── Permission gating ───────────────────────────────────────────

    @Test
    void list_withAiUsageViewOnly_returns200() throws Exception {
        // AUDITOR holds AI_USAGE_VIEW but NOT QUOTA_OVERRIDE.
        AuthResponse auditor = registerStaff("quota-auditor1@example.com", StaffRole.AUDITOR);

        mockMvc.perform(get("/api/v1/admin/quota-overrides")
                        .header("Authorization", "Bearer " + auditor.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void create_withoutQuotaOverridePermission_returns403() throws Exception {
        AuthResponse auditor = registerStaff("quota-auditor2@example.com", StaffRole.AUDITOR);
        registerAndLogin("quota-auditortarget@example.com");

        String body = objectMapper.writeValueAsString(Map.of(
                "targetType", "USER", "targetId", userId("quota-auditortarget@example.com"),
                "kind", "CREDIT", "amountCents", 100, "reason", "should be blocked"));

        mockMvc.perform(post("/api/v1/admin/quota-overrides")
                        .header("Authorization", "Bearer " + auditor.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void revoke_withoutQuotaOverridePermission_returns403() throws Exception {
        AuthResponse auditor = registerStaff("quota-auditor3@example.com", StaffRole.AUDITOR);

        mockMvc.perform(delete("/api/v1/admin/quota-overrides/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + auditor.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void quotaEndpoints_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/quota-overrides"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_asNonStaff_returns403() throws Exception {
        String token = registerAndGetToken("quota-nonstaff@example.com");

        mockMvc.perform(post("/api/v1/admin/quota-overrides")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"USER\",\"targetId\":\"" + UUID.randomUUID()
                                + "\",\"kind\":\"UNLIMITED\",\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
    }
}
