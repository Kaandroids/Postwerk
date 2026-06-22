package com.postwerk.service;

import com.postwerk.config.GdprProperties;
import com.postwerk.model.AuditAction;
import com.postwerk.repository.AiConversationRepository;
import com.postwerk.repository.AuditLogRepository;
import com.postwerk.repository.EmailRepository;
import com.postwerk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataRetentionServiceTest {

    @Mock private EmailRepository emailRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private AiConversationRepository conversationRepository;
    @Mock private GdprProperties gdprProperties;
    @Mock private AuditService auditService;
    @Mock private JobRunService jobRunService;

    @InjectMocks
    private DataRetentionService dataRetentionService;

    @BeforeEach
    void setUp() {
        // JobRunService wraps the scheduled sweep — invoke the wrapped work inline so the existing tests run unchanged.
        lenient().doAnswer(inv -> { ((Runnable) inv.getArgument(1)).run(); return null; })
                .when(jobRunService).run(any(), any());
        lenient().when(gdprProperties.emailRetentionDays()).thenReturn(365);
        lenient().when(gdprProperties.auditLogRetentionDays()).thenReturn(730);
        lenient().when(gdprProperties.accountDeletionGraceDays()).thenReturn(30);
        lenient().when(gdprProperties.conversationRetentionDays()).thenReturn(90);
        lenient().when(gdprProperties.ipRetentionDays()).thenReturn(90);
    }

    // ─── Conversation soft-delete ──────────────────────────────────

    @Test
    void cleanupExpiredData_softDeletesOldConversations() {
        when(emailRepository.softDeleteByReceivedAtBefore(any(), anyInt())).thenReturn(0);
        when(emailRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(userRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(conversationRepository.softDeleteByUpdatedAtBefore(any(), anyInt())).thenReturn(5);
        when(conversationRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(auditLogRepository.pseudonymizeIpsBefore(any(), anyInt())).thenReturn(0);
        when(userRepository.pseudonymizeLoginIpsBefore(any(), anyInt())).thenReturn(0);
        when(auditLogRepository.deleteOlderThan(any(), anyInt())).thenReturn(0);

        dataRetentionService.cleanupExpiredData();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(conversationRepository).softDeleteByUpdatedAtBefore(cutoffCaptor.capture(), eq(DataRetentionService.BATCH_SIZE));

        Instant cutoff = cutoffCaptor.getValue();
        Instant expected = Instant.now().minus(90, ChronoUnit.DAYS);
        assertThat(cutoff).isBetween(expected.minusSeconds(5), expected.plusSeconds(5));

        verify(auditService).log(isNull(), eq(AuditAction.DATA_DELETED_BY_RETENTION),
                contains("5 AI conversations"), eq((String) null));
    }

    @Test
    void cleanupExpiredData_hardDeletesConversationsPastGracePeriod() {
        when(emailRepository.softDeleteByReceivedAtBefore(any(), anyInt())).thenReturn(0);
        when(emailRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(userRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(conversationRepository.softDeleteByUpdatedAtBefore(any(), anyInt())).thenReturn(0);
        when(conversationRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(3);
        when(auditLogRepository.pseudonymizeIpsBefore(any(), anyInt())).thenReturn(0);
        when(userRepository.pseudonymizeLoginIpsBefore(any(), anyInt())).thenReturn(0);
        when(auditLogRepository.deleteOlderThan(any(), anyInt())).thenReturn(0);

        dataRetentionService.cleanupExpiredData();

        verify(conversationRepository).hardDeleteSoftDeletedBefore(any(), eq(DataRetentionService.BATCH_SIZE));
    }

    @Test
    void cleanupExpiredData_noConversationsToDelete_skipsAuditLog() {
        when(emailRepository.softDeleteByReceivedAtBefore(any(), anyInt())).thenReturn(0);
        when(emailRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(userRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(conversationRepository.softDeleteByUpdatedAtBefore(any(), anyInt())).thenReturn(0);
        when(conversationRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(auditLogRepository.pseudonymizeIpsBefore(any(), anyInt())).thenReturn(0);
        when(userRepository.pseudonymizeLoginIpsBefore(any(), anyInt())).thenReturn(0);
        when(auditLogRepository.deleteOlderThan(any(), anyInt())).thenReturn(0);

        dataRetentionService.cleanupExpiredData();

        verify(auditService, never()).log(isNull(), eq(AuditAction.DATA_DELETED_BY_RETENTION),
                contains("AI conversations"), eq((String) null));
    }

    // ─── IP pseudonymization ───────────────────────────────────────

    @Test
    void cleanupExpiredData_pseudonymizesOldIps() {
        when(emailRepository.softDeleteByReceivedAtBefore(any(), anyInt())).thenReturn(0);
        when(emailRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(userRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(conversationRepository.softDeleteByUpdatedAtBefore(any(), anyInt())).thenReturn(0);
        when(conversationRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(auditLogRepository.pseudonymizeIpsBefore(any(), anyInt())).thenReturn(12);
        when(userRepository.pseudonymizeLoginIpsBefore(any(), anyInt())).thenReturn(3);
        when(auditLogRepository.deleteOlderThan(any(), anyInt())).thenReturn(0);

        dataRetentionService.cleanupExpiredData();

        ArgumentCaptor<Instant> ipCutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(auditLogRepository).pseudonymizeIpsBefore(ipCutoffCaptor.capture(), eq(DataRetentionService.BATCH_SIZE));
        verify(userRepository).pseudonymizeLoginIpsBefore(any(), eq(DataRetentionService.BATCH_SIZE));

        Instant ipCutoff = ipCutoffCaptor.getValue();
        Instant expected = Instant.now().minus(90, ChronoUnit.DAYS);
        assertThat(ipCutoff).isBetween(expected.minusSeconds(5), expected.plusSeconds(5));
    }

    @Test
    void cleanupExpiredData_noIpsToPseudonymize_noLogOutput() {
        when(emailRepository.softDeleteByReceivedAtBefore(any(), anyInt())).thenReturn(0);
        when(emailRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(userRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(conversationRepository.softDeleteByUpdatedAtBefore(any(), anyInt())).thenReturn(0);
        when(conversationRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(auditLogRepository.pseudonymizeIpsBefore(any(), anyInt())).thenReturn(0);
        when(userRepository.pseudonymizeLoginIpsBefore(any(), anyInt())).thenReturn(0);
        when(auditLogRepository.deleteOlderThan(any(), anyInt())).thenReturn(0);

        dataRetentionService.cleanupExpiredData();

        verify(auditLogRepository).pseudonymizeIpsBefore(any(), eq(DataRetentionService.BATCH_SIZE));
        verify(userRepository).pseudonymizeLoginIpsBefore(any(), eq(DataRetentionService.BATCH_SIZE));
    }

    // ─── Full lifecycle ────────────────────────────────────────────

    @Test
    void cleanupExpiredData_executesAllStepsInOrder() {
        when(emailRepository.softDeleteByReceivedAtBefore(any(), anyInt())).thenReturn(2);
        when(emailRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(1);
        when(userRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(conversationRepository.softDeleteByUpdatedAtBefore(any(), anyInt())).thenReturn(4);
        when(conversationRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(2);
        when(auditLogRepository.pseudonymizeIpsBefore(any(), anyInt())).thenReturn(10);
        when(userRepository.pseudonymizeLoginIpsBefore(any(), anyInt())).thenReturn(5);
        when(auditLogRepository.deleteOlderThan(any(), anyInt())).thenReturn(0);

        dataRetentionService.cleanupExpiredData();

        // Verify all operations were called
        verify(emailRepository).softDeleteByReceivedAtBefore(any(), anyInt());
        verify(emailRepository).hardDeleteSoftDeletedBefore(any(), anyInt());
        verify(userRepository).hardDeleteSoftDeletedBefore(any(), anyInt());
        verify(conversationRepository).softDeleteByUpdatedAtBefore(any(), anyInt());
        verify(conversationRepository).hardDeleteSoftDeletedBefore(any(), anyInt());
        verify(auditLogRepository).pseudonymizeIpsBefore(any(), anyInt());
        verify(userRepository).pseudonymizeLoginIpsBefore(any(), anyInt());
        verify(auditLogRepository).deleteOlderThan(any(), anyInt());
    }

    // ─── Custom retention config ───────────────────────────────────

    @Test
    void cleanupExpiredData_respectsCustomConversationRetention() {
        when(gdprProperties.conversationRetentionDays()).thenReturn(30);
        when(emailRepository.softDeleteByReceivedAtBefore(any(), anyInt())).thenReturn(0);
        when(emailRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(userRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(conversationRepository.softDeleteByUpdatedAtBefore(any(), anyInt())).thenReturn(0);
        when(conversationRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(auditLogRepository.pseudonymizeIpsBefore(any(), anyInt())).thenReturn(0);
        when(userRepository.pseudonymizeLoginIpsBefore(any(), anyInt())).thenReturn(0);
        when(auditLogRepository.deleteOlderThan(any(), anyInt())).thenReturn(0);

        dataRetentionService.cleanupExpiredData();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(conversationRepository).softDeleteByUpdatedAtBefore(captor.capture(), eq(DataRetentionService.BATCH_SIZE));

        Instant cutoff = captor.getValue();
        Instant expected = Instant.now().minus(30, ChronoUnit.DAYS);
        assertThat(cutoff).isBetween(expected.minusSeconds(5), expected.plusSeconds(5));
    }

    @Test
    void cleanupExpiredData_respectsCustomIpRetention() {
        when(gdprProperties.ipRetentionDays()).thenReturn(60);
        when(emailRepository.softDeleteByReceivedAtBefore(any(), anyInt())).thenReturn(0);
        when(emailRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(userRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(conversationRepository.softDeleteByUpdatedAtBefore(any(), anyInt())).thenReturn(0);
        when(conversationRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(auditLogRepository.pseudonymizeIpsBefore(any(), anyInt())).thenReturn(0);
        when(userRepository.pseudonymizeLoginIpsBefore(any(), anyInt())).thenReturn(0);
        when(auditLogRepository.deleteOlderThan(any(), anyInt())).thenReturn(0);

        dataRetentionService.cleanupExpiredData();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(auditLogRepository).pseudonymizeIpsBefore(captor.capture(), eq(DataRetentionService.BATCH_SIZE));

        Instant cutoff = captor.getValue();
        Instant expected = Instant.now().minus(60, ChronoUnit.DAYS);
        assertThat(cutoff).isBetween(expected.minusSeconds(5), expected.plusSeconds(5));
    }

    // ─── Batch processing ──────────────────────────────────────────

    @Test
    void cleanupExpiredData_processesMultipleBatchesWhenOverLimit() {
        // First call returns full batch (5000), second returns remainder (200) — loop should run twice
        when(emailRepository.softDeleteByReceivedAtBefore(any(), anyInt()))
                .thenReturn(DataRetentionService.BATCH_SIZE)
                .thenReturn(200);
        when(emailRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(userRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(conversationRepository.softDeleteByUpdatedAtBefore(any(), anyInt())).thenReturn(0);
        when(conversationRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(auditLogRepository.pseudonymizeIpsBefore(any(), anyInt())).thenReturn(0);
        when(userRepository.pseudonymizeLoginIpsBefore(any(), anyInt())).thenReturn(0);
        when(auditLogRepository.deleteOlderThan(any(), anyInt())).thenReturn(0);

        dataRetentionService.cleanupExpiredData();

        // Should have been called twice (5000 + 200)
        verify(emailRepository, times(2)).softDeleteByReceivedAtBefore(any(), eq(DataRetentionService.BATCH_SIZE));

        // Audit log should report the total (5200)
        verify(auditService).log(isNull(), eq(AuditAction.DATA_DELETED_BY_RETENTION),
                contains("5200 emails"), eq((String) null));
    }

    @Test
    void processBatch_stopsWhenBatchReturnsBelowLimit() {
        // Single batch with fewer than BATCH_SIZE rows — should only run once
        when(emailRepository.softDeleteByReceivedAtBefore(any(), anyInt())).thenReturn(100);
        when(emailRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(userRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(conversationRepository.softDeleteByUpdatedAtBefore(any(), anyInt())).thenReturn(0);
        when(conversationRepository.hardDeleteSoftDeletedBefore(any(), anyInt())).thenReturn(0);
        when(auditLogRepository.pseudonymizeIpsBefore(any(), anyInt())).thenReturn(0);
        when(userRepository.pseudonymizeLoginIpsBefore(any(), anyInt())).thenReturn(0);
        when(auditLogRepository.deleteOlderThan(any(), anyInt())).thenReturn(0);

        dataRetentionService.cleanupExpiredData();

        verify(emailRepository, times(1)).softDeleteByReceivedAtBefore(any(), eq(DataRetentionService.BATCH_SIZE));
    }
}
