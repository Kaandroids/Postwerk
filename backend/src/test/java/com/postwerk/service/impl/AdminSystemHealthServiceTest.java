package com.postwerk.service.impl;

import com.postwerk.repository.AiTokenUsageRepository;
import com.postwerk.repository.AuditLogRepository;
import com.postwerk.repository.AutomationDelayedEmailRepository;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.PendingActionRepository;
import com.postwerk.service.AuditService;
import com.postwerk.service.MaintenanceModeService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminSystemHealthServiceImpl}. The subsystem probes (DB pool, Redis, cache,
 * Micrometer, circuit breakers) bind to live infrastructure and are integration-test territory; these
 * cover the parts that DON'T probe infra: the audit-derived event feed and the maintenance-mode
 * get/set passthrough. The live-infra collaborators are mocked only to satisfy the constructor.
 */
@ExtendWith(MockitoExtension.class)
class AdminSystemHealthServiceTest {

    @Mock private DataSource dataSource;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private CacheManager cacheManager;
    @Mock private MeterRegistry meterRegistry;
    @Mock private CircuitBreakerRegistry circuitBreakerRegistry;
    @Mock private PendingActionRepository pendingActionRepository;
    @Mock private AutomationDelayedEmailRepository delayedEmailRepository;
    @Mock private EmailAccountRepository emailAccountRepository;
    @Mock private AiTokenUsageRepository aiTokenUsageRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AuditService auditService;
    @Mock private MaintenanceModeService maintenanceModeService;

    @InjectMocks
    private AdminSystemHealthServiceImpl service;

    @Test
    void events_emptyAuditFeed_returnsEmpty() {
        when(auditLogRepository.findTop20ByActionInOrderByCreatedAtDesc(any())).thenReturn(List.of());

        assertThat(service.events()).isEmpty();
    }

    @Test
    void getMaintenance_reflectsServiceState() {
        Instant now = Instant.now();
        when(maintenanceModeService.isEnabled()).thenReturn(true);
        when(maintenanceModeService.message()).thenReturn("Scheduled downtime");
        when(maintenanceModeService.updatedAt()).thenReturn(now);

        var r = service.getMaintenance();

        assertThat(r.enabled()).isTrue();
        assertThat(r.message()).isEqualTo("Scheduled downtime");
        assertThat(r.updatedAt()).isEqualTo(now);
    }

    @Test
    void setMaintenance_appliesToServiceAndReturnsState() {
        when(maintenanceModeService.isEnabled()).thenReturn(true);
        when(maintenanceModeService.message()).thenReturn("Down for upgrade");

        var r = service.setMaintenance(true, "Down for upgrade", UUID.randomUUID(), "127.0.0.1");

        verify(maintenanceModeService).set(true, "Down for upgrade");
        assertThat(r.enabled()).isTrue();
        assertThat(r.message()).isEqualTo("Down for upgrade");
    }
}
