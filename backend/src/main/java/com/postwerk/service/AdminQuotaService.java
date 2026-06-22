package com.postwerk.service;

import com.postwerk.dto.admin.QuotaKpisResponse;
import com.postwerk.dto.admin.QuotaOverrideRequest;
import com.postwerk.dto.admin.QuotaOverrideResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Platform-staff management of AI quota overrides (admin "Quota Overrides" page). Resolves every
 * override to an enforcement organization and reuses {@link QuotaService#effectiveCapCents} so the
 * list shows the exact cap enforcement will apply.
 *
 * @since 1.0
 */
public interface AdminQuotaService {

    /**
     * Paginated, filtered list of overrides.
     *
     * @param search     matches the resolved target name / email-or-slug / id (blank disables);
     * @param targetType {@code USER} | {@code ORG} (null disables);
     * @param kind       {@code CREDIT} | {@code CAP} | {@code UNLIMITED} (null disables);
     * @param status     {@code active} | {@code expired} (null disables);
     * @param expiry     {@code next7} | {@code next30} (expiry window; null disables);
     * @param pageable   page (default sort createdAt desc, size 10 — applied by the controller).
     */
    Page<QuotaOverrideResponse> list(String search, String targetType, String kind,
                                     String status, String expiry, Pageable pageable);

    /** Creates an override; resolves the enforcement org, snapshots the granting staffer, audits. */
    QuotaOverrideResponse create(QuotaOverrideRequest request, UUID staffUserId, String ipAddress);

    /** Updates an override (target is locked: the stored target/org is kept). Audits. */
    QuotaOverrideResponse update(UUID id, QuotaOverrideRequest request, UUID staffUserId, String ipAddress);

    /** Hard-deletes (revokes) an override. Audits. */
    void revoke(UUID id, UUID staffUserId, String ipAddress);

    /** KPI strip totals. */
    QuotaKpisResponse kpis();
}
