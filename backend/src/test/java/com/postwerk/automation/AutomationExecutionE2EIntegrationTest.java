package com.postwerk.automation;

import com.postwerk.BaseIntegrationTest;
import com.postwerk.TestFixtures;
import com.postwerk.model.Automation;
import com.postwerk.model.AutomationEdge;
import com.postwerk.model.AutomationNode;
import com.postwerk.model.Category;
import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.postwerk.model.EmailAutomationTrace;
import com.postwerk.model.Organization;
import com.postwerk.model.User;
import com.postwerk.model.enums.NodeType;
import com.postwerk.model.enums.TraceStatus;
import com.postwerk.repository.AutomationEdgeRepository;
import com.postwerk.repository.AutomationNodeRepository;
import com.postwerk.repository.AutomationRepository;
import com.postwerk.repository.CategoryRepository;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.EmailAutomationTraceRepository;
import com.postwerk.repository.EmailRepository;
import com.postwerk.repository.OrganizationRepository;
import com.postwerk.service.AutomationExecutorService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-pipeline proof: an inbound email runs through a real, persisted automation
 * (TRIGGER → LABEL) via the production execution entry point ({@link AutomationExecutorService#processEmail})
 * against a real Testcontainers Postgres DB — and the configured action takes effect
 * (the email is labelled) with a SUCCESS trace recorded. No mocks of the executor.
 */
class AutomationExecutionE2EIntegrationTest extends BaseIntegrationTest {

    @Autowired private AutomationExecutorService executor;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private EmailAccountRepository emailAccountRepository;
    @Autowired private AutomationRepository automationRepository;
    @Autowired private AutomationNodeRepository nodeRepository;
    @Autowired private AutomationEdgeRepository edgeRepository;
    @Autowired private EmailRepository emailRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private EmailAutomationTraceRepository traceRepository;
    @PersistenceContext private EntityManager em;

    @Test
    void inboundEmail_runsAutomation_appliesLabel_andRecordsSuccessTrace() {
        // ── Seed: user + auto-created personal org ──────────────────────────────
        registerAndLogin("e2e-exec@example.com");
        User user = userRepository.findByEmail("e2e-exec@example.com").orElseThrow();
        Organization org = organizationRepository
                .findByOwnerUserIdAndPersonalTrue(user.getId()).orElseThrow();

        // NOTE: entity ids are @GeneratedValue(UUID); fixtures pre-set them, which makes Hibernate
        // treat them as detached. Null the id before persist so Hibernate generates it (INSERT),
        // then read the generated id back.

        // ── Seed: a category the LABEL node will assign ─────────────────────────
        Category cat = TestFixtures.createCategory(user.getId());
        cat.setId(null);
        cat.setOrganizationId(org.getId());
        cat.setName("Bestellung");
        em.persist(cat);
        UUID categoryId = cat.getId();

        // ── Seed: email account (org-scoped) ────────────────────────────────────
        EmailAccount account = TestFixtures.createEmailAccount(user.getId());
        account.setId(null);
        account.setOrganizationId(org.getId());
        em.persist(account);
        UUID accountId = account.getId();

        // ── Seed: automation TRIGGER → LABEL(category) bound to the account ──────
        Automation auto = TestFixtures.createAutomation(user.getId());
        auto.setId(null);
        auto.setOrganizationId(org.getId());
        auto.setAccountIds(new UUID[]{accountId});
        em.persist(auto);

        AutomationNode trigger = TestFixtures.createNode(auto, NodeType.TRIGGER, "Posteingang");
        trigger.setId(null);
        em.persist(trigger);

        AutomationNode label = TestFixtures.createNode(auto, NodeType.LABEL, "Kategorisieren");
        label.setId(null);
        label.setConfig("{\"categoryId\":\"" + categoryId + "\"}");
        em.persist(label);

        // The EMAIL trigger routes a fresh (non-reply) inbound email out of its "new-email" handle,
        // so the edge feeding the LABEL node must originate from that handle (not "output").
        AutomationEdge edge = TestFixtures.createEdge(auto, trigger, label, "new-email");
        edge.setId(null);
        em.persist(edge);

        // ── Seed: an unprocessed inbound email on the account ───────────────────
        Email email = TestFixtures.createEmail(accountId);
        email.setId(null);
        em.persist(email);
        UUID emailId = email.getId();

        // Flush + clear so the native processable-automation query sees committed-state
        // rows and the executor loads fresh managed entities (realistic execution).
        em.flush();
        em.clear();

        // ── ACT: run the real production execution pipeline ─────────────────────
        Email fresh = emailRepository.findById(emailId).orElseThrow();
        executor.processEmail(fresh);

        em.flush();
        em.clear();

        // ── ASSERT: trace first (did the automation actually run?), then side effects ──
        List<EmailAutomationTrace> traces = traceRepository.findByEmailIdOrderByStartedAtDesc(emailId);
        assertThat(traces)
                .as("a trace should be recorded — proves the automation MATCHED and RAN for this email")
                .isNotEmpty();
        assertThat(traces.get(0).getStatus())
                .as("the automation run should complete with SUCCESS")
                .isEqualTo(TraceStatus.SUCCESS);

        Email after = emailRepository.findById(emailId).orElseThrow();
        assertThat(after.isProcessed())
                .as("email should be marked processed after automation run")
                .isTrue();
        assertThat(after.getLabels())
                .as("LABEL node should have written the category onto the email's labels")
                .contains(categoryId.toString());
    }
}
