package com.postwerk.util;

import java.util.UUID;

/**
 * Holds the active organization id for the current request thread so {@code AuditService} can stamp
 * audit entries with the org the action happened in, without threading the org through every call site.
 *
 * <p>Set (with the validated org) by {@code OrgContextService.resolve(...)} during request handling and
 * cleared after each request by {@code OrgAuditContextFilter}. Reads on threads with no active request
 * (scheduled jobs) return {@code null}, which is correct — those actions are not org-scoped.</p>
 *
 * @since 1.0
 */
public final class OrgAuditContext {

    private static final ThreadLocal<UUID> CURRENT_ORG = new ThreadLocal<>();

    private OrgAuditContext() {
    }

    public static void set(UUID organizationId) {
        CURRENT_ORG.set(organizationId);
    }

    public static UUID get() {
        return CURRENT_ORG.get();
    }

    public static void clear() {
        CURRENT_ORG.remove();
    }
}
