package com.postwerk.controller;

import com.postwerk.BaseIntegrationTest;
import com.postwerk.dto.auth.AuthResponse;
import com.postwerk.model.Automation;
import com.postwerk.model.EmailAccount;
import com.postwerk.model.Membership;
import com.postwerk.model.Organization;
import com.postwerk.model.User;
import com.postwerk.model.enums.AutomationKind;
import com.postwerk.model.enums.AutomationStatus;
import com.postwerk.model.enums.AutomationType;
import com.postwerk.model.enums.MembershipStatus;
import com.postwerk.model.enums.OrgRole;
import com.postwerk.repository.AutomationRepository;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.MembershipRepository;
import com.postwerk.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminOrganizationControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private AutomationRepository automationRepository;

    @Autowired
    private EmailAccountRepository emailAccountRepository;

    private Organization createTeamOrg(UUID ownerId, String name) {
        Organization org = organizationRepository.save(Organization.builder()
                .name(name).ownerUserId(ownerId).personal(false).build());
        membershipRepository.save(Membership.builder()
                .organizationId(org.getId()).userId(ownerId)
                .role(OrgRole.OWNER).status(MembershipStatus.ACTIVE).build());
        return org;
    }

    // ─── List ────────────────────────────────────────────────────────

    @Test
    void list_asAdmin_returnsPage() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("org-list-admin@example.com");

        mockMvc.perform(get("/api/v1/admin/organizations")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void list_asUser_returns403() throws Exception {
        String token = registerAndGetToken("org-list-user@example.com");

        mockMvc.perform(get("/api/v1/admin/organizations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ─── Detail ──────────────────────────────────────────────────────

    @Test
    void getOrganization_asAdmin_returnsDetailWithMembers() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("org-detail-admin@example.com");
        registerAndLogin("org-owner@example.com");
        User owner = userRepository.findByEmail("org-owner@example.com").orElseThrow();
        Organization org = createTeamOrg(owner.getId(), "Acme Team");

        mockMvc.perform(get("/api/v1/admin/organizations/" + org.getId())
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Team"))
                .andExpect(jsonPath("$.personal").value(false))
                .andExpect(jsonPath("$.members", hasSize(1)))
                .andExpect(jsonPath("$.ownerEmail").value("org-owner@example.com"));
    }

    // ─── Detail tabs: automations & mailboxes ────────────────────────

    @Test
    void getOrganizationAutomations_asAdmin_returnsOrgScopedRows() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("org-autos-admin@example.com");
        registerAndLogin("org-autos-owner@example.com");
        User owner = userRepository.findByEmail("org-autos-owner@example.com").orElseThrow();
        Organization org = createTeamOrg(owner.getId(), "Autos Team");

        automationRepository.save(Automation.builder()
                .userId(owner.getId()).organizationId(org.getId())
                .name("Org Automation").type(AutomationType.EMAIL)
                .kind(AutomationKind.AUTOMATION).status(AutomationStatus.ACTIVE)
                .accountIds(new UUID[]{}).color("#3b82f6").build());

        mockMvc.perform(get("/api/v1/admin/organizations/" + org.getId() + "/automations")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Org Automation"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].kind").value("AUTOMATION"));
    }

    @Test
    void getOrganizationAutomations_asUser_returns403() throws Exception {
        String token = registerAndGetToken("org-autos-user@example.com");

        mockMvc.perform(get("/api/v1/admin/organizations/" + UUID.randomUUID() + "/automations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void getOrganizationMailboxes_asAdmin_returnsSafeMetadata() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("org-mbx-admin@example.com");
        registerAndLogin("org-mbx-owner@example.com");
        User owner = userRepository.findByEmail("org-mbx-owner@example.com").orElseThrow();
        Organization org = createTeamOrg(owner.getId(), "Mailbox Team");

        emailAccountRepository.save(EmailAccount.builder()
                .userId(owner.getId()).organizationId(org.getId())
                .email("team@example.com").displayName("Team Inbox")
                .color("#3b82f6").readEnabled(true).writeEnabled(true)
                .imapPassword("super-secret").build());

        mockMvc.perform(get("/api/v1/admin/organizations/" + org.getId() + "/mailboxes")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value("team@example.com"))
                .andExpect(jsonPath("$[0].displayName").value("Team Inbox"))
                // never leaks encrypted secrets
                .andExpect(jsonPath("$[0].imapPassword").doesNotExist())
                .andExpect(jsonPath("$[0].smtpPassword").doesNotExist());
    }

    @Test
    void getOrganizationMailboxes_unknownOrg_returns404() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("org-mbx-admin2@example.com");

        mockMvc.perform(get("/api/v1/admin/organizations/" + UUID.randomUUID() + "/mailboxes")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isNotFound());
    }

    // ─── Transfer ownership ──────────────────────────────────────────

    @Test
    void transferOwnership_asAdmin_movesOwnership() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("org-transfer-admin@example.com");
        registerAndLogin("org-owner2@example.com");
        registerAndLogin("org-member2@example.com");
        User owner = userRepository.findByEmail("org-owner2@example.com").orElseThrow();
        User member = userRepository.findByEmail("org-member2@example.com").orElseThrow();
        Organization org = createTeamOrg(owner.getId(), "Transfer Team");
        membershipRepository.save(Membership.builder()
                .organizationId(org.getId()).userId(member.getId())
                .role(OrgRole.MEMBER).status(MembershipStatus.ACTIVE).build());

        String body = objectMapper.writeValueAsString(Map.of("newOwnerUserId", member.getId()));

        mockMvc.perform(post("/api/v1/admin/organizations/" + org.getId() + "/transfer-ownership")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerUserId").value(member.getId().toString()));
    }

    @Test
    void transferOwnership_toNonMember_returns400() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("org-transfer-admin2@example.com");
        registerAndLogin("org-owner3@example.com");
        User owner = userRepository.findByEmail("org-owner3@example.com").orElseThrow();
        Organization org = createTeamOrg(owner.getId(), "Lonely Team");

        String body = objectMapper.writeValueAsString(Map.of("newOwnerUserId", UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/admin/organizations/" + org.getId() + "/transfer-ownership")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ─── Delete ──────────────────────────────────────────────────────

    @Test
    void deleteOrganization_personal_returns400() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("org-del-admin@example.com");
        registerAndLogin("org-personal@example.com");
        User u = userRepository.findByEmail("org-personal@example.com").orElseThrow();
        Organization personal = organizationRepository.findByOwnerUserIdAndPersonalTrue(u.getId()).orElseThrow();

        mockMvc.perform(delete("/api/v1/admin/organizations/" + personal.getId())
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteOrganization_team_returns204() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("org-del-admin2@example.com");
        registerAndLogin("org-team-owner@example.com");
        User u = userRepository.findByEmail("org-team-owner@example.com").orElseThrow();
        Organization org = createTeamOrg(u.getId(), "Deletable Team");

        mockMvc.perform(delete("/api/v1/admin/organizations/" + org.getId())
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isNoContent());
    }

    // ─── Suspend / Activate ──────────────────────────────────────────

    @Test
    void suspendOrganization_team_setsSuspendedState() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("org-susp-admin@example.com");
        registerAndLogin("org-susp-owner@example.com");
        User u = userRepository.findByEmail("org-susp-owner@example.com").orElseThrow();
        Organization org = createTeamOrg(u.getId(), "Suspendable Team");

        String body = objectMapper.writeValueAsString(Map.of("reason", "Non-payment"));

        mockMvc.perform(post("/api/v1/admin/organizations/" + org.getId() + "/suspend")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspendedAt").isNotEmpty())
                .andExpect(jsonPath("$.suspensionReason").value("Non-payment"));
    }

    @Test
    void suspendOrganization_personal_returns400() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("org-susp-admin2@example.com");
        registerAndLogin("org-susp-personal@example.com");
        User u = userRepository.findByEmail("org-susp-personal@example.com").orElseThrow();
        Organization personal = organizationRepository.findByOwnerUserIdAndPersonalTrue(u.getId()).orElseThrow();

        mockMvc.perform(post("/api/v1/admin/organizations/" + personal.getId() + "/suspend")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void activateOrganization_clearsSuspension() throws Exception {
        AuthResponse admin = registerAndMakeAdmin("org-act-admin@example.com");
        registerAndLogin("org-act-owner@example.com");
        User u = userRepository.findByEmail("org-act-owner@example.com").orElseThrow();
        Organization org = createTeamOrg(u.getId(), "Reactivatable Team");
        org.setSuspendedAt(Instant.now());
        org.setSuspensionReason("temp");
        organizationRepository.saveAndFlush(org);

        mockMvc.perform(post("/api/v1/admin/organizations/" + org.getId() + "/activate")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspendedAt").isEmpty())
                .andExpect(jsonPath("$.suspensionReason").isEmpty());
    }

    @Test
    void suspendedOrg_blocksTenantAccess_returns403() throws Exception {
        AuthResponse user = registerAndLogin("org-susp-tenant@example.com");
        User u = userRepository.findByEmail("org-susp-tenant@example.com").orElseThrow();
        Organization org = createTeamOrg(u.getId(), "Frozen Co");

        // Active org is reachable by its owner.
        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + user.accessToken())
                        .header("X-Org-Id", org.getId().toString()))
                .andExpect(status().isOk());

        org.setSuspendedAt(Instant.now());
        organizationRepository.saveAndFlush(org);

        // Once suspended, the same tenant request is rejected at context resolution.
        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + user.accessToken())
                        .header("X-Org-Id", org.getId().toString()))
                .andExpect(status().isForbidden());
    }
}
