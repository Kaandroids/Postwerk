package com.postwerk.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postwerk.dto.TestModeResultResponse;
import com.postwerk.model.Automation;
import com.postwerk.model.AutomationTestModeResult;
import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.postwerk.model.EmailAutomationTrace;
import com.postwerk.model.EmailNodeTrace;
import com.postwerk.model.enums.NodeType;
import com.postwerk.repository.AutomationRepository;
import com.postwerk.repository.AutomationTestModeResultRepository;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.EmailRepository;
import com.postwerk.service.AutomationExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the per-email simulation and per-result delete added to {@link TestModeServiceImpl}.
 * Repositories and the executor are mocked; a real {@link ObjectMapper} serializes the simulated
 * actions. Locks the org-ownership guards (automation, email, and email-belongs-to-org via its
 * account) and the dry-run → record flow.
 */
@ExtendWith(MockitoExtension.class)
class TestModeServiceImplTest {

    @Mock private AutomationTestModeResultRepository resultRepository;
    @Mock private AutomationRepository automationRepository;
    @Mock private EmailRepository emailRepository;
    @Mock private EmailAccountRepository emailAccountRepository;
    @Mock private AutomationExecutorService executor;

    private TestModeServiceImpl service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID automationId = UUID.randomUUID();
    private final UUID emailId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TestModeServiceImpl(resultRepository, automationRepository, emailRepository,
                emailAccountRepository, executor, new ObjectMapper());
    }

    private Automation automation() {
        return Automation.builder().id(automationId).organizationId(orgId).build();
    }

    private Email email() {
        return Email.builder().id(emailId).emailAccountId(accountId)
                .subject("Invoice #42").fromAddress("billing@acme.com").receivedAt(Instant.now()).build();
    }

    // ── simulateEmail: ownership guards ──────────────────────────────────

    @Test
    void simulateEmail_throwsWhenAutomationNotInOrg() {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.simulateEmail(orgId, automationId, emailId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Automation not found");
        verifyNoInteractions(executor);
        verify(resultRepository, never()).save(any());
    }

    @Test
    void simulateEmail_throwsWhenEmailNotFound() {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation()));
        when(emailRepository.findById(emailId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.simulateEmail(orgId, automationId, emailId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email not found");
        verifyNoInteractions(executor);
    }

    @Test
    void simulateEmail_throwsWhenEmailBelongsToAnotherOrg() {
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation()));
        when(emailRepository.findById(emailId)).thenReturn(Optional.of(email()));
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.simulateEmail(orgId, automationId, emailId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email not found");
        verifyNoInteractions(executor);
        verify(resultRepository, never()).save(any());
    }

    // ── simulateEmail: happy path ────────────────────────────────────────

    @Test
    void simulateEmail_runsDryRunAndRecordsSimulatedActions() {
        Automation automation = automation();
        Email email = email();
        EmailAccount account = EmailAccount.builder().id(accountId).organizationId(orgId).build();
        UUID traceId = UUID.randomUUID();
        EmailAutomationTrace trace = EmailAutomationTrace.builder()
                .id(traceId)
                .nodeTraces(List.of(
                        EmailNodeTrace.builder().nodeType(NodeType.TRIGGER).nodeLabel("Incoming").build(),
                        EmailNodeTrace.builder().nodeType(NodeType.SEND_EMAIL).nodeLabel("Notify team")
                                .resultDetail("{\"to\":\"team@acme.com\"}").build()))
                .build();

        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));
        when(emailRepository.findById(emailId)).thenReturn(Optional.of(email));
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.of(account));
        when(executor.runTestDryRun(automation, email, account)).thenReturn(trace);
        when(resultRepository.save(any())).thenAnswer(inv -> {
            AutomationTestModeResult r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });

        TestModeResultResponse response = service.simulateEmail(orgId, automationId, emailId);

        verify(executor).runTestDryRun(automation, email, account);
        assertThat(response.id()).isNotNull();
        assertThat(response.emailId()).isEqualTo(emailId);
        assertThat(response.traceId()).isEqualTo(traceId);
        assertThat(response.emailSubject()).isEqualTo("Invoice #42");
        // Only the action node becomes a simulated action; the TRIGGER node is skipped.
        assertThat(response.simulatedActions()).hasSize(1);
        assertThat(response.simulatedActions().get(0).nodeLabel()).isEqualTo("Notify team");
        assertThat(response.simulatedActions().get(0).description()).contains("team@acme.com");
    }

    @Test
    void simulateEmail_surfacesForEachLoopInTheFeed() {
        Automation automation = automation();
        Email email = email();
        EmailAccount account = EmailAccount.builder().id(accountId).organizationId(orgId).build();
        EmailAutomationTrace trace = EmailAutomationTrace.builder()
                .id(UUID.randomUUID())
                .nodeTraces(List.of(
                        // FOREACH is control-flow (not an ACTION_TYPE) but must still appear in the feed.
                        EmailNodeTrace.builder().nodeType(NodeType.FOREACH).nodeLabel("Each attachment")
                                .resultDetail("{\"count\":3,\"source\":\"email.attachments\",\"alias\":\"item\"}").build(),
                        EmailNodeTrace.builder().nodeType(NodeType.EXTRACT).nodeLabel("Read PDF").build()))
                .build();

        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));
        when(emailRepository.findById(emailId)).thenReturn(Optional.of(email));
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.of(account));
        when(executor.runTestDryRun(automation, email, account)).thenReturn(trace);
        when(resultRepository.save(any())).thenAnswer(inv -> {
            AutomationTestModeResult r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });

        TestModeResultResponse response = service.simulateEmail(orgId, automationId, emailId);

        // EXTRACT is not an action → dropped; only the FOREACH iterator surfaces.
        assertThat(response.simulatedActions()).hasSize(1);
        assertThat(response.simulatedActions().get(0).nodeType()).isEqualTo("FOREACH");
        assertThat(response.simulatedActions().get(0).nodeLabel()).isEqualTo("Each attachment");
        assertThat(response.simulatedActions().get(0).description()).isEqualTo("Loop over email.attachments (3×)");
    }

    @Test
    void simulateEmail_notesTruncationOnAForEachLoop() {
        Automation automation = automation();
        Email email = email();
        EmailAccount account = EmailAccount.builder().id(accountId).organizationId(orgId).build();
        EmailAutomationTrace trace = EmailAutomationTrace.builder()
                .id(UUID.randomUUID())
                .nodeTraces(List.of(
                        EmailNodeTrace.builder().nodeType(NodeType.FOREACH).nodeLabel("Each row")
                                .resultDetail("{\"count\":100,\"source\":\"rows\",\"alias\":\"row\",\"truncatedFrom\":250}").build()))
                .build();

        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation));
        when(emailRepository.findById(emailId)).thenReturn(Optional.of(email));
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.of(account));
        when(executor.runTestDryRun(automation, email, account)).thenReturn(trace);
        when(resultRepository.save(any())).thenAnswer(inv -> {
            AutomationTestModeResult r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });

        TestModeResultResponse response = service.simulateEmail(orgId, automationId, emailId);

        assertThat(response.simulatedActions()).hasSize(1);
        assertThat(response.simulatedActions().get(0).description())
                .isEqualTo("Loop over rows (100×) — truncated from 250");
    }

    // ── deleteResult ─────────────────────────────────────────────────────

    @Test
    void deleteResult_deletesWhenOwnedByAutomation() {
        AutomationTestModeResult result = AutomationTestModeResult.builder()
                .id(UUID.randomUUID()).automationId(automationId).build();
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation()));
        when(resultRepository.findById(result.getId())).thenReturn(Optional.of(result));

        service.deleteResult(orgId, automationId, result.getId());

        verify(resultRepository).delete(result);
    }

    @Test
    void deleteResult_throwsWhenAutomationNotInOrg() {
        UUID resultId = UUID.randomUUID();
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteResult(orgId, automationId, resultId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Automation not found");
        verify(resultRepository, never()).delete(any());
    }

    @Test
    void deleteResult_throwsWhenResultNotFound() {
        UUID resultId = UUID.randomUUID();
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation()));
        when(resultRepository.findById(resultId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteResult(orgId, automationId, resultId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("result not found");
        verify(resultRepository, never()).delete(any());
    }

    @Test
    void deleteResult_throwsWhenResultBelongsToAnotherAutomation() {
        UUID resultId = UUID.randomUUID();
        AutomationTestModeResult foreign = AutomationTestModeResult.builder()
                .id(resultId).automationId(UUID.randomUUID()).build(); // different automation
        when(automationRepository.findByIdAndOrganizationId(automationId, orgId)).thenReturn(Optional.of(automation()));
        when(resultRepository.findById(resultId)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.deleteResult(orgId, automationId, resultId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
        verify(resultRepository, never()).delete(any());
    }
}
