package com.postwerk.service;

import com.postwerk.model.AuditLog;
import com.postwerk.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Persists audit entries off the request thread.
 *
 * <p>Separated from {@link AuditService} so the {@code @Async} boundary is a genuine cross-bean proxy
 * call (a self-invocation would bypass the proxy and run synchronously), while {@code AuditService}
 * still captures the active org id synchronously — on the request thread — before this async write runs.</p>
 *
 * @since 1.0
 */
@Component
public class AuditWriter {

    private static final Logger log = LoggerFactory.getLogger(AuditWriter.class);

    private final AuditLogRepository auditLogRepository;

    public AuditWriter(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Async
    public void save(AuditLog entry) {
        try {
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to write audit log: action={}, userId={}", entry.getAction(), entry.getUserId(), e);
        }
    }
}
