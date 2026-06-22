package com.postwerk.service.impl;

import com.postwerk.TestFixtures;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.EmailAccount;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.OrganizationRepository;
import com.postwerk.repository.UserRepository;
import com.postwerk.service.AuditService;
import com.postwerk.service.EmailSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminEmailHealthServiceImpl} — the pure derivation/aggregation logic of the
 * admin Email-Health screen: health state from {@code lastSyncStatus}, KPI counts, server-cluster
 * status thresholds, and the in-memory health filter. No live IMAP/SMTP involved.
 */
@ExtendWith(MockitoExtension.class)
class AdminEmailHealthServiceTest {

    @Mock private EmailAccountRepository emailAccountRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailSyncService emailSyncService;
    @Mock private AuditService auditService;

    @InjectMocks
    private AdminEmailHealthServiceImpl service;

    /** A mailbox with a chosen IMAP host + persisted sync status; no owner/org so no batch lookups fire. */
    private EmailAccount acc(String imapHost, String syncStatus, boolean paused) {
        EmailAccount a = TestFixtures.createEmailAccount(UUID.randomUUID());
        a.setUserId(null);
        a.setOrganizationId(null);
        a.setImapHost(imapHost);
        a.setSmtpHost(null);
        a.setLastSyncStatus(syncStatus);
        a.setLastSyncAt(Instant.now());
        a.setPaused(paused);
        return a;
    }

    @Test
    void kpis_countsMailboxesByHealthState() {
        when(emailAccountRepository.findAll()).thenReturn(List.of(
                acc("h", "OK", false),
                acc("h", "AUTH_ERROR", false),
                acc("h", "CONN_ERROR", false),
                acc("h", "OK", true) // healthy status but paused → not counted as healthy
        ));

        var kpis = service.kpis();

        assertThat(kpis.total()).isEqualTo(4);
        assertThat(kpis.failing()).isEqualTo(1);     // CONN_ERROR
        assertThat(kpis.authErrors()).isEqualTo(1);  // AUTH_ERROR
        assertThat(kpis.paused()).isEqualTo(1);
        assertThat(kpis.healthy()).isEqualTo(1);     // only the un-paused OK box
    }

    @Test
    void clusters_marksServerDownWhenTwoOrMoreFailing() {
        when(emailAccountRepository.findAll()).thenReturn(List.of(
                acc("imap.acme.com", "CONN_ERROR", false),
                acc("imap.acme.com", "CONN_ERROR", false),
                acc("imap.acme.com", "OK", false)
        ));

        var clusters = service.clusters();

        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0).host()).isEqualTo("imap.acme.com");
        assertThat(clusters.get(0).failing()).isEqualTo(2);
        assertThat(clusters.get(0).status()).isEqualTo("down"); // failing >= 2
    }

    @Test
    void listMailboxes_healthFilter_returnsOnlyFailing() {
        when(emailAccountRepository.findAll()).thenReturn(List.of(
                acc("h", "OK", false),
                acc("h", "CONN_ERROR", false)
        ));

        var page = service.listMailboxes(null, null, "failing", null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).health()).isEqualTo("failing");
    }

    @Test
    void getMailbox_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(emailAccountRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMailbox(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
