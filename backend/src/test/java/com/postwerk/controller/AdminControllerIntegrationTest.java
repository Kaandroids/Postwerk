package com.postwerk.controller;

import com.postwerk.BaseIntegrationTest;
import com.postwerk.TestFixtures;
import com.postwerk.dto.admin.PlanRequest;
import com.postwerk.dto.admin.RoleUpdateRequest;
import com.postwerk.dto.admin.StaffRoleUpdateRequest;
import com.postwerk.dto.auth.AuthResponse;
import com.postwerk.model.AuditAction;
import com.postwerk.model.AuditLog;
import com.postwerk.model.EmailAccount;
import com.postwerk.model.Membership;
import com.postwerk.model.Organization;
import com.postwerk.model.Plan;
import com.postwerk.model.User;
import com.postwerk.model.enums.MembershipStatus;
import com.postwerk.model.enums.OrgRole;
import com.postwerk.model.enums.StaffRole;
import com.postwerk.repository.AuditLogRepository;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.MembershipRepository;
import com.postwerk.repository.OrganizationRepository;
import com.postwerk.repository.PlanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private EmailAccountRepository emailAccountRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    // ─── Dashboard Stats ─────────────────────────────────────────────

    @Test
    void getStats_asAdmin_returns200() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("admin-stats@example.com");

        mockMvc.perform(get("/api/v1/admin/stats")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").isNumber());
    }

    @Test
    void getStats_asUser_returns403() throws Exception {
        String token = registerAndGetToken("user-stats@example.com");

        mockMvc.perform(get("/api/v1/admin/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ─── User Management ─────────────────────────────────────────────

    @Test
    void getUsers_asAdmin_returnsPage() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("admin-users@example.com");

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void updateRole_asAdmin_returns200() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("admin-role@example.com");
        registerAndLogin("target-user@example.com");
        User targetUser = userRepository.findByEmail("target-user@example.com").orElseThrow();

        RoleUpdateRequest request = new RoleUpdateRequest("ADMIN");

        mockMvc.perform(patch("/api/v1/admin/users/" + targetUser.getId() + "/role")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void updateRole_selfChange_returns400() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("admin-self@example.com");
        User adminUser = userRepository.findByEmail("admin-self@example.com").orElseThrow();

        RoleUpdateRequest request = new RoleUpdateRequest("USER");

        mockMvc.perform(patch("/api/v1/admin/users/" + adminUser.getId() + "/role")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ─── User detail tabs: organizations, mailboxes ─────────────────

    @Test
    void getUserOrganizations_asAdmin_includesPersonalAndTeamOrgs() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("admin-userorgs@example.com");
        registerAndLogin("userorgs-target@example.com");
        User target = userRepository.findByEmail("userorgs-target@example.com").orElseThrow();

        Organization team = organizationRepository.save(Organization.builder()
                .name("Member Team").ownerUserId(UUID.randomUUID()).personal(false).build());
        membershipRepository.save(Membership.builder()
                .organizationId(team.getId()).userId(target.getId())
                .role(OrgRole.MEMBER).status(MembershipStatus.ACTIVE).build());

        mockMvc.perform(get("/api/v1/admin/users/" + target.getId() + "/organizations")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                // registration auto-creates a personal org; plus the team membership above
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[*].name", hasItem("Member Team")));
    }

    @Test
    void getUserMailboxes_asAdmin_returnsSafeMetadataNoSecrets() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("admin-usermbx@example.com");
        registerAndLogin("usermbx-target@example.com");
        User target = userRepository.findByEmail("usermbx-target@example.com").orElseThrow();
        Organization personal = organizationRepository.findByOwnerUserIdAndPersonalTrue(target.getId()).orElseThrow();

        emailAccountRepository.save(EmailAccount.builder()
                .userId(target.getId()).organizationId(personal.getId())
                .email("mine@example.com").displayName("My Inbox")
                .color("#10b981").readEnabled(true).writeEnabled(true)
                .imapPassword("top-secret").build());

        mockMvc.perform(get("/api/v1/admin/users/" + target.getId() + "/mailboxes")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value("mine@example.com"))
                .andExpect(jsonPath("$[0].imapPassword").doesNotExist())
                .andExpect(jsonPath("$[0].smtpPassword").doesNotExist());
    }

    @Test
    void getUserMailboxes_asUser_returns403() throws Exception {
        String token = registerAndGetToken("usermbx-forbidden@example.com");

        mockMvc.perform(get("/api/v1/admin/users/" + UUID.randomUUID() + "/mailboxes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ─── Audit Log (organization filter) ─────────────────────────────

    @Test
    void getAuditLog_withOrganizationId_filtersByOrg() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("admin-auditorg@example.com");
        registerAndLogin("audit-actor@example.com");
        User actor = userRepository.findByEmail("audit-actor@example.com").orElseThrow();

        // audit_log.organization_id has an FK to organizations(id), so persist real orgs (V71).
        Organization orgA = organizationRepository.save(Organization.builder()
                .name("Audit Org A").ownerUserId(actor.getId()).personal(false).build());
        Organization orgB = organizationRepository.save(Organization.builder()
                .name("Audit Org B").ownerUserId(actor.getId()).personal(false).build());
        auditLogRepository.save(AuditLog.builder()
                .userId(actor.getId()).organizationId(orgA.getId())
                .action(AuditAction.USER_LOGIN).detail("in org A").build());
        auditLogRepository.save(AuditLog.builder()
                .userId(actor.getId()).organizationId(orgB.getId())
                .action(AuditAction.USER_LOGIN).detail("in org B").build());

        mockMvc.perform(get("/api/v1/admin/audit-log")
                        .param("organizationId", orgA.getId().toString())
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].detail").value("in org A"));
    }

    // ─── Staff Identity & Roles ──────────────────────────────────────

    @Test
    void me_asAdmin_returnsStaffRoleAndPermissions() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("admin-me@example.com");

        mockMvc.perform(get("/api/v1/admin/me")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staffRole").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.permissions", hasItem("STAFF_MANAGE")));
    }

    @Test
    void me_asUser_returnsNullStaffRoleAndNoPermissions() throws Exception {
        String token = registerAndGetToken("user-me@example.com");

        // /admin/me is allowlisted for ANY authenticated user (see SecurityConfig + the
        // endpoint Javadoc): a non-staff caller gets 200 with staffRole=null and empty
        // permissions so the UI can hide admin features. (NOT 403 — that was the
        // pre-RBAC behaviour; this assertion was stale and only surfaced once CI began
        // actually running the integration tests.)
        mockMvc.perform(get("/api/v1/admin/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staffRole").isEmpty())
                .andExpect(jsonPath("$.permissions", hasSize(0)));
    }

    @Test
    void updateStaffRole_asAdmin_assignsSupport() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("admin-staffrole@example.com");
        registerAndLogin("staff-target@example.com");
        User target = userRepository.findByEmail("staff-target@example.com").orElseThrow();

        StaffRoleUpdateRequest request = new StaffRoleUpdateRequest("SUPPORT");

        mockMvc.perform(patch("/api/v1/admin/users/" + target.getId() + "/staff-role")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staffRole").value("SUPPORT"));
    }

    @Test
    void updateStaffRole_selfChange_returns400() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("admin-staffrole-self@example.com");
        User adminUser = userRepository.findByEmail("admin-staffrole-self@example.com").orElseThrow();

        StaffRoleUpdateRequest request = new StaffRoleUpdateRequest("SUPPORT");

        mockMvc.perform(patch("/api/v1/admin/users/" + adminUser.getId() + "/staff-role")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ─── Plans ───────────────────────────────────────────────────────

    @Test
    void getPlans_asAdmin_returns200() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("admin-plans@example.com");

        mockMvc.perform(get("/api/v1/admin/plans")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void createPlan_asAdmin_returns201() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("admin-create-plan@example.com");

        PlanRequest request = new PlanRequest("Premium", 50000, 20, 10, new BigDecimal("19.99"), false, 500, 5, true);

        mockMvc.perform(post("/api/v1/admin/plans")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Premium"))
                .andExpect(jsonPath("$.tokenLimit").value(50000));
    }

    @Test
    void updatePlan_asAdmin_returns200() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("admin-update-plan@example.com");

        Plan plan = planRepository.save(Plan.builder()
                .name("OldPlan")
                .tokenLimit(1000)
                .automationLimit(1)
                .emailAccountLimit(1)
                .price(new BigDecimal("4.99"))
                .build());

        PlanRequest request = new PlanRequest("UpdatedPlan", 2000, 5, 3, new BigDecimal("9.99"), false, 200, 2, true);

        mockMvc.perform(put("/api/v1/admin/plans/" + plan.getId())
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("UpdatedPlan"))
                .andExpect(jsonPath("$.tokenLimit").value(2000));
    }

    @Test
    void deletePlan_asAdmin_returns204() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("admin-delete-plan@example.com");

        Plan plan = planRepository.save(Plan.builder()
                .name("ToDelete")
                .tokenLimit(500)
                .automationLimit(1)
                .emailAccountLimit(1)
                .price(new BigDecimal("0.00"))
                .build());

        mockMvc.perform(delete("/api/v1/admin/plans/" + plan.getId())
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void createPlan_includesCostLimitCents() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("admin-cost-plan@example.com");

        PlanRequest request = new PlanRequest("CostPlan", 10000, 5, 3, new BigDecimal("9.99"), false, 750, 3, true);

        mockMvc.perform(post("/api/v1/admin/plans")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("CostPlan"))
                .andExpect(jsonPath("$.costLimitCents").value(750));
    }

    @Test
    void assignPlan_asAdmin_returns200() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("admin-assign@example.com");
        registerAndLogin("assign-target@example.com");
        User targetUser = userRepository.findByEmail("assign-target@example.com").orElseThrow();

        Plan plan = planRepository.save(Plan.builder()
                .name("AssignMe")
                .tokenLimit(5000)
                .automationLimit(3)
                .emailAccountLimit(2)
                .price(new BigDecimal("0.00"))
                .costLimitCents(300)
                .build());

        String body = objectMapper.writeValueAsString(java.util.Map.of("planId", plan.getId()));

        mockMvc.perform(patch("/api/v1/admin/users/" + targetUser.getId() + "/plan")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetUser.getId().toString()));
    }

    @Test
    void assignPlan_asUser_returns403() throws Exception {
        String token = registerAndGetToken("user-assign@example.com");

        mockMvc.perform(patch("/api/v1/admin/users/" + java.util.UUID.randomUUID() + "/plan")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"" + java.util.UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    // ─── Users support tooling: staff notes ──────────────────────────

    @Test
    void addAndListUserNotes_asAdmin_roundTrips() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("admin-notes@example.com");
        registerAndLogin("notes-target@example.com");
        User target = userRepository.findByEmail("notes-target@example.com").orElseThrow();

        String body = objectMapper.writeValueAsString(java.util.Map.of("body", "Called about billing"));

        mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/notes")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Called about billing"))
                .andExpect(jsonPath("$.authorEmail").value("admin-notes@example.com"))
                .andExpect(jsonPath("$.id").exists());

        mockMvc.perform(get("/api/v1/admin/users/" + target.getId() + "/notes")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].body").value("Called about billing"));
    }

    @Test
    void deleteUserNote_asAuthor_returns204() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("admin-notedel@example.com");
        registerAndLogin("notedel-target@example.com");
        User target = userRepository.findByEmail("notedel-target@example.com").orElseThrow();

        String created = mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/notes")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("body", "delete me"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID noteId = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        mockMvc.perform(delete("/api/v1/admin/users/" + target.getId() + "/notes/" + noteId)
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/users/" + target.getId() + "/notes")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void deleteUserNote_byNonAuthorWithoutUserManage_returns403() throws Exception {
        // Author is a SUPER_ADMIN; the deleter is a MODERATOR (has USER_VIEW, NOT USER_MANAGE).
        AuthResponse author = registerAndMakeAdmin("admin-noteowner@example.com");
        AuthResponse moderator = registerStaff("mod-notedel@example.com", StaffRole.MODERATOR);
        registerAndLogin("note403-target@example.com");
        User target = userRepository.findByEmail("note403-target@example.com").orElseThrow();

        String created = mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/notes")
                        .header("Authorization", "Bearer " + author.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("body", "owned by admin"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID noteId = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        mockMvc.perform(delete("/api/v1/admin/users/" + target.getId() + "/notes/" + noteId)
                        .header("Authorization", "Bearer " + moderator.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void addUserNote_asNonStaff_returns403() throws Exception {
        String token = registerAndGetToken("notes-forbidden@example.com");

        mockMvc.perform(post("/api/v1/admin/users/" + UUID.randomUUID() + "/notes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserNotes_unknownUser_returns404() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("admin-notes404@example.com");

        mockMvc.perform(get("/api/v1/admin/users/" + UUID.randomUUID() + "/notes")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isNotFound());
    }

    // ─── Force password reset ─────────────────────────────────────────

    @Test
    void forcePasswordReset_asAdmin_returns204() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("admin-pwreset@example.com");
        registerAndLogin("pwreset-target@example.com");
        User target = userRepository.findByEmail("pwreset-target@example.com").orElseThrow();

        mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/reset-password")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void forcePasswordReset_unknownUser_returns404() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("admin-pwreset404@example.com");

        mockMvc.perform(post("/api/v1/admin/users/" + UUID.randomUUID() + "/reset-password")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void forcePasswordReset_asNonStaff_returns403() throws Exception {
        String token = registerAndGetToken("pwreset-forbidden@example.com");

        mockMvc.perform(post("/api/v1/admin/users/" + UUID.randomUUID() + "/reset-password")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ─── Sessions: count + revoke ─────────────────────────────────────

    @Test
    void getUserSessions_asAdmin_returnsCount() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("admin-sessions@example.com");
        // Logging in creates a refresh token (one tracked session) for the target.
        registerAndLogin("sessions-target@example.com");
        User target = userRepository.findByEmail("sessions-target@example.com").orElseThrow();

        mockMvc.perform(get("/api/v1/admin/users/" + target.getId() + "/sessions")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSessions").isNumber());
    }

    @Test
    void revokeUserSessions_asAdmin_returns200WithZero() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("admin-revoke@example.com");
        registerAndLogin("revoke-target@example.com");
        User target = userRepository.findByEmail("revoke-target@example.com").orElseThrow();

        mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/revoke-sessions")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSessions").value(0));
    }

    @Test
    void revokeUserSessions_asNonStaff_returns403() throws Exception {
        String token = registerAndGetToken("revoke-forbidden@example.com");

        mockMvc.perform(post("/api/v1/admin/users/" + UUID.randomUUID() + "/revoke-sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ─── Unauthenticated ─────────────────────────────────────────────

    @Test
    void adminEndpoints_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats"))
                .andExpect(status().isUnauthorized());
    }

    /** Registers a user, assigns the given staff role (without ADMIN platform role), and logs in. */
    private AuthResponse registerStaff(String email, StaffRole staffRole) {
        registerAndLogin(email);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setStaffRole(staffRole);
        userRepository.saveAndFlush(user);
        return authService.login(new com.postwerk.dto.auth.LoginRequest(email, TestFixtures.TEST_PASSWORD), TestFixtures.TEST_IP);
    }
}
