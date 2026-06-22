package com.postwerk.service;

import com.postwerk.model.AuditAction;
import com.postwerk.model.AuditLog;
import com.postwerk.util.IpResolverUtil;
import com.postwerk.util.OrgAuditContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Service for asynchronously recording audit log entries that track user actions,
 * security events, and system operations. Supports before/after diff generation for change tracking.
 *
 * @since 1.0
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final ObjectMapper DIFF_MAPPER = new ObjectMapper();

    private final AuditWriter auditWriter;

    public AuditService(AuditWriter auditWriter) {
        this.auditWriter = auditWriter;
    }

    public void log(UUID userId, AuditAction action, String detail, String ipAddress) {
        // Stamp the org the action happened in, captured synchronously on the request thread
        // (OrgAuditContext) before the async DB write runs.
        auditWriter.save(AuditLog.builder()
                .userId(userId)
                .organizationId(OrgAuditContext.get())
                .action(action)
                .detail(detail)
                .ipAddress(ipAddress)
                .build());
    }

    public void log(UUID userId, AuditAction action, String ipAddress) {
        log(userId, action, null, ipAddress);
    }

    public void log(UUID userId, AuditAction action, String detail, HttpServletRequest request) {
        log(userId, action, detail, IpResolverUtil.extractIp(request));
    }

    public void log(UUID userId, AuditAction action, HttpServletRequest request) {
        log(userId, action, null, IpResolverUtil.extractIp(request));
    }

    /**
     * Logs an update action with a computed before/after diff. If no fields changed
     * (diff is null), falls back to {@code fallbackLabel}.
     */
    public void logDiff(UUID userId, AuditAction action, Map<String, Object> before, Map<String, Object> after,
                        String fallbackLabel, String ipAddress) {
        String diff = buildDiff(before, after);
        log(userId, action, diff != null ? diff : fallbackLabel, ipAddress);
    }

    /**
     * Compares before/after maps, filters to only changed fields,
     * and returns a JSON string: {"before":{...}, "after":{...}}.
     * Returns null if nothing changed.
     */
    public static String buildDiff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> changedBefore = new LinkedHashMap<>();
        Map<String, Object> changedAfter = new LinkedHashMap<>();

        for (String key : after.keySet()) {
            Object oldVal = before.get(key);
            Object newVal = after.get(key);
            if (!Objects.equals(oldVal, newVal)) {
                changedBefore.put(key, oldVal);
                changedAfter.put(key, newVal);
            }
        }

        if (changedBefore.isEmpty()) return null;

        try {
            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("before", changedBefore);
            diff.put("after", changedAfter);
            return DIFF_MAPPER.writeValueAsString(diff);
        } catch (Exception e) {
            return null;
        }
    }
}
