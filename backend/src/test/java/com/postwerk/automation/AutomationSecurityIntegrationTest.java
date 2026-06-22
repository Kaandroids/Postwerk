package com.postwerk.automation;

import com.postwerk.BaseIntegrationTest;
import com.postwerk.TestFixtures;
import com.postwerk.model.Automation;
import com.postwerk.model.AutomationEdge;
import com.postwerk.model.AutomationNode;
import com.postwerk.model.Membership;
import com.postwerk.model.Organization;
import com.postwerk.model.User;
import com.postwerk.model.enums.MembershipStatus;
import com.postwerk.model.enums.NodeType;
import com.postwerk.model.enums.OrgRole;
import com.postwerk.repository.OrganizationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security boundary proofs (multi-tenant isolation, hidden-copy confidentiality, admin gate).
 * These exercise enforcement that had NO test coverage and are the highest-risk surfaces for an
 * enterprise SaaS. All requests go through the real Spring Security filter chain via MockMvc.
 */
class AutomationSecurityIntegrationTest extends BaseIntegrationTest {

    @Autowired private OrganizationRepository organizationRepository;
    @PersistenceContext private EntityManager em;

    /** Persist an automation into a specific org (ids are @GeneratedValue → null before persist). */
    private Automation seedAutomation(UUID userId, UUID orgId, boolean hidden) {
        Automation a = TestFixtures.createAutomation(userId);
        a.setId(null);
        a.setOrganizationId(orgId);
        a.setHidden(hidden);
        a.setAccountIds(new UUID[]{});
        em.persist(a);
        return a;
    }

    private Organization personalOrgOf(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        return organizationRepository.findByOwnerUserIdAndPersonalTrue(user.getId()).orElseThrow();
    }

    // ── 1. Cross-org read is denied (the core multi-tenant guarantee) ───────────
    @Test
    void getAutomation_fromAnotherOrg_isNotFound() throws Exception {
        String token = registerAndGetToken("sec-user-a@example.com");
        User userA = userRepository.findByEmail("sec-user-a@example.com").orElseThrow();

        // An automation that lives in a DIFFERENT organization than the caller's active (personal) org.
        Organization foreignOrg = new Organization();
        foreignOrg.setName("Foreign Org");
        foreignOrg.setOwnerUserId(UUID.randomUUID());
        foreignOrg.setPersonal(false);
        em.persist(foreignOrg);
        Automation foreign = seedAutomation(userA.getId(), foreignOrg.getId(), false);
        em.flush();

        mockMvc.perform(get("/api/v1/automations/" + foreign.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ── 2. List is org-scoped: caller sees only their active org's automations ──
    @Test
    void listAutomations_returnsOnlyActiveOrgRows() throws Exception {
        String token = registerAndGetToken("sec-user-b@example.com");
        User userB = userRepository.findByEmail("sec-user-b@example.com").orElseThrow();
        Organization orgA = personalOrgOf("sec-user-b@example.com");

        Automation mine = seedAutomation(userB.getId(), orgA.getId(), false);

        Organization otherOrg = new Organization();
        otherOrg.setName("Other Org");
        otherOrg.setOwnerUserId(UUID.randomUUID());
        otherOrg.setPersonal(false);
        em.persist(otherOrg);
        seedAutomation(userB.getId(), otherOrg.getId(), false); // must NOT appear
        em.flush();

        mockMvc.perform(get("/api/v1/automations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(mine.getId().toString()));
    }

    // ── 3. Hidden (private marketplace) copy never leaks its flow internals ─────
    @Test
    void getAutomation_whenHidden_returnsMetadataButNoFlow() throws Exception {
        String token = registerAndGetToken("sec-user-c@example.com");
        User userC = userRepository.findByEmail("sec-user-c@example.com").orElseThrow();
        Organization org = personalOrgOf("sec-user-c@example.com");

        Automation hidden = seedAutomation(userC.getId(), org.getId(), true);
        // Seed real nodes + an edge so we prove they are SUPPRESSED, not merely absent.
        AutomationNode n1 = TestFixtures.createNode(hidden, NodeType.TRIGGER, "Trigger");
        n1.setId(null);
        em.persist(n1);
        AutomationNode n2 = TestFixtures.createNode(hidden, NodeType.LABEL, "Label");
        n2.setId(null);
        em.persist(n2);
        AutomationEdge e = TestFixtures.createEdge(hidden, n1, n2, "new-email");
        e.setId(null);
        em.persist(e);
        em.flush();

        mockMvc.perform(get("/api/v1/automations/" + hidden.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(hidden.getId().toString()))
                .andExpect(jsonPath("$.name").exists())          // metadata still visible
                .andExpect(jsonPath("$.nodes", hasSize(0)))       // flow suppressed
                .andExpect(jsonPath("$.edges", hasSize(0)))
                .andExpect(jsonPath("$.constants", hasSize(0)));  // constant values withheld
    }

    // ── 4. Admin surface is gated: a normal user cannot reach it ────────────────
    @Test
    void adminEndpoint_asNonStaff_isForbidden() throws Exception {
        String token = registerAndGetToken("sec-user-d@example.com");

        mockMvc.perform(get("/api/v1/admin/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ── 5. Spoofing an org you're not a member of via X-Org-Id is denied ────────
    @Test
    void requestWithForeignOrgHeader_isForbidden() throws Exception {
        String token = registerAndGetToken("sec-user-e@example.com");

        Organization foreignOrg = new Organization();
        foreignOrg.setName("Not My Org");
        foreignOrg.setOwnerUserId(UUID.randomUUID());
        foreignOrg.setPersonal(false);
        em.persist(foreignOrg);
        em.flush();

        mockMvc.perform(get("/api/v1/automations")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Org-Id", foreignOrg.getId().toString()))
                .andExpect(status().isForbidden());
    }

    // ── 6. Unauthenticated access is rejected ───────────────────────────────────
    @Test
    void listAutomations_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/automations"))
                .andExpect(status().isUnauthorized());
    }
}
