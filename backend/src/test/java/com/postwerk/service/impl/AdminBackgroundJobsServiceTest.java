package com.postwerk.service.impl;

import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.enums.ApprovalStatus;
import com.postwerk.repository.AutomationDelayedEmailRepository;
import com.postwerk.repository.JobRunRepository;
import com.postwerk.repository.PendingActionRepository;
import com.postwerk.service.AuditService;
import com.postwerk.service.DataRetentionService;
import com.postwerk.service.JobRunService;
import com.postwerk.service.ScheduledEmailSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminBackgroundJobsServiceImpl} — the static 4-job registry status derivation
 * and live queue-depth/tone computation. Job history + queue counts come from mocked repositories.
 */
@ExtendWith(MockitoExtension.class)
class AdminBackgroundJobsServiceTest {

    @Mock private JobRunRepository jobRunRepository;
    @Mock private JobRunService jobRunService;
    @Mock private PendingActionRepository pendingActionRepository;
    @Mock private AutomationDelayedEmailRepository delayedEmailRepository;
    @Mock private AuditService auditService;
    @Mock private ScheduledEmailSyncService emailSync;
    @Mock private AutomationExecutorServiceImpl automationExecutor;
    @Mock private DataRetentionService dataRetention;

    @InjectMocks
    private AdminBackgroundJobsServiceImpl service;

    @Test
    void listJobs_allHealthyWhenNoHistoryAndNotPaused() {
        when(jobRunRepository.countsSince(any())).thenReturn(List.of());
        when(jobRunRepository.findTopByJobIdOrderByStartedAtDesc(anyString())).thenReturn(null);
        when(jobRunService.isPaused(anyString())).thenReturn(false);

        var jobs = service.listJobs();

        assertThat(jobs).hasSize(4); // the platform's 4 recurring jobs
        assertThat(jobs).allMatch(j -> "healthy".equals(j.status()));
    }

    @Test
    void queues_reportsDepthAndTone() {
        when(pendingActionRepository.countByStatus(ApprovalStatus.PENDING)).thenReturn(5L);
        when(pendingActionRepository.countByStatus(ApprovalStatus.APPROVED)).thenReturn(10L);
        when(pendingActionRepository.countByStatus(ApprovalStatus.REJECTED)).thenReturn(2L);
        when(delayedEmailRepository.countByProcessedFalse()).thenReturn(150L);

        var queues = service.queues();

        assertThat(queues).hasSize(2);
        // Approval queue: 5 pending ≤ 100 → clear.
        assertThat(queues.get(0).pending()).isEqualTo(5);
        assertThat(queues.get(0).tone()).isEqualTo("clear");
        // Delayed queue: 150 awaiting > 100 → backlog.
        assertThat(queues.get(1).pending()).isEqualTo(150);
        assertThat(queues.get(1).tone()).isEqualTo("backlog");
    }

    @Test
    void getJob_unknownId_throws() {
        assertThatThrownBy(() -> service.getJob("nonexistent-job"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
