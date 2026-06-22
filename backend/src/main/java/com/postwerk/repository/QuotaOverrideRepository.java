package com.postwerk.repository;

import com.postwerk.model.QuotaOverride;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link QuotaOverride} entities (admin "Quota Overrides").
 *
 * <p>Enforcement reads all overrides for an organization on every AI quota check
 * ({@code findByOrganizationId}); the admin list/KPIs read a filtered, batch-friendly slice.</p>
 *
 * @since 1.0
 */
public interface QuotaOverrideRepository extends JpaRepository<QuotaOverride, UUID> {

    /** All overrides (active or expired) for an enforcement org — used by {@code effectiveCap}. */
    List<QuotaOverride> findByOrganizationId(UUID organizationId);

    /**
     * Active overrides (no expiry, or expiry after {@code now}) for a set of enforcement orgs — used by
     * the list/KPI batch effective-cap so a page of N rows resolves caps in a single query, not N.
     */
    @Query("SELECT o FROM QuotaOverride o WHERE o.organizationId IN :orgIds " +
           "AND (o.expiresAt IS NULL OR o.expiresAt > :now)")
    List<QuotaOverride> findActiveByOrganizationIds(@Param("orgIds") Collection<UUID> orgIds,
                                                    @Param("now") Instant now);

    /**
     * Paged admin search. Filters use non-null sentinels (empty string, {@code 0}, {@code false}) so a
     * disabled filter never binds an untyped {@code null} parameter — Postgres can't infer the type of a
     * bare {@code :param IS NULL} placeholder ("could not determine data type of parameter").
     * <ul>
     *   <li>{@code targetType} — {@code ""} disables; else the {@code USER}/{@code ORG} enum name;</li>
     *   <li>{@code kind} — {@code ""} disables; else the {@code CREDIT}/{@code CAP}/{@code UNLIMITED} name;</li>
     *   <li>{@code statusMode} — {@code 0} disables; {@code 1} → active (not expired); {@code 2} → expired;</li>
     *   <li>{@code expiryFilter} — {@code false} disables; {@code true} → expiry within ({@code now},
     *       {@code expiryWindowEnd}] (expiring-soon). {@code expiryWindowEnd} is always non-null.</li>
     * </ul>
     * Text search is applied in Java over the (small) page rows so it can match the resolved target
     * name/email/slug (which live on User/Organization, not on this table).
     */
    @Query("SELECT o FROM QuotaOverride o WHERE " +
           "(:targetType = '' OR CAST(o.targetType AS string) = :targetType) " +
           "AND (:kind = '' OR CAST(o.kind AS string) = :kind) " +
           "AND (:statusMode = 0 " +
           "     OR (:statusMode = 1 AND (o.expiresAt IS NULL OR o.expiresAt > :now)) " +
           "     OR (:statusMode = 2 AND o.expiresAt IS NOT NULL AND o.expiresAt <= :now)) " +
           "AND (:expiryFilter = FALSE " +
           "     OR (o.expiresAt IS NOT NULL AND o.expiresAt > :now AND o.expiresAt <= :expiryWindowEnd))")
    Page<QuotaOverride> search(@Param("targetType") String targetType,
                               @Param("kind") String kind,
                               @Param("statusMode") int statusMode,
                               @Param("expiryFilter") boolean expiryFilter,
                               @Param("expiryWindowEnd") Instant expiryWindowEnd,
                               @Param("now") Instant now,
                               Pageable pageable);

    /** Count of currently-active overrides (KPI: "Active overrides"). */
    @Query("SELECT COUNT(o) FROM QuotaOverride o WHERE o.expiresAt IS NULL OR o.expiresAt > :now")
    long countActive(@Param("now") Instant now);

    /** Count of active overrides expiring in ({@code now}, {@code windowEnd}] (KPI: "Expiring in 7 days"). */
    @Query("SELECT COUNT(o) FROM QuotaOverride o WHERE o.expiresAt IS NOT NULL " +
           "AND o.expiresAt > :now AND o.expiresAt <= :windowEnd")
    long countExpiringBetween(@Param("now") Instant now, @Param("windowEnd") Instant windowEnd);

    /** All currently-active overrides (KPI helpers that scan over the active set: credit-this-month, over-80%). */
    @Query("SELECT o FROM QuotaOverride o WHERE o.expiresAt IS NULL OR o.expiresAt > :now")
    List<QuotaOverride> findAllActive(@Param("now") Instant now);
}
