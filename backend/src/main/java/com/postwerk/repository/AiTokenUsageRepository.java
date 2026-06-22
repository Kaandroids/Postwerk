package com.postwerk.repository;

import com.postwerk.model.AiTokenUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AiTokenUsage} entities.
 * Persists per-request AI token consumption records for usage tracking and billing.
 *
 * @since 1.0
 */
public interface AiTokenUsageRepository extends JpaRepository<AiTokenUsage, UUID> {

    @Query("SELECT COALESCE(SUM(a.promptTokens), 0) FROM AiTokenUsage a")
    long sumPromptTokens();

    @Query("SELECT COALESCE(SUM(a.outputTokens), 0) FROM AiTokenUsage a")
    long sumOutputTokens();

    @Query("SELECT COALESCE(SUM(a.totalTokens), 0) FROM AiTokenUsage a")
    long sumTotalTokens();

    @Query("SELECT COALESCE(SUM(a.billableChars), 0) FROM AiTokenUsage a")
    long sumBillableChars();

    @Query("SELECT a.model, COALESCE(SUM(a.promptTokens), 0), COALESCE(SUM(a.outputTokens), 0), COALESCE(SUM(a.totalTokens), 0) " +
           "FROM AiTokenUsage a GROUP BY a.model ORDER BY SUM(a.totalTokens) DESC")
    List<Object[]> sumTokensGroupByModel();

    @Query("SELECT a.operation, COALESCE(SUM(a.promptTokens), 0), COALESCE(SUM(a.outputTokens), 0), COALESCE(SUM(a.totalTokens), 0) " +
           "FROM AiTokenUsage a GROUP BY a.operation ORDER BY SUM(a.totalTokens) DESC")
    List<Object[]> sumTokensGroupByOperation();

    @Query("SELECT a.userId, COALESCE(SUM(a.promptTokens), 0), COALESCE(SUM(a.outputTokens), 0), COALESCE(SUM(a.totalTokens), 0), COUNT(a) " +
           "FROM AiTokenUsage a GROUP BY a.userId ORDER BY SUM(a.totalTokens) DESC")
    List<Object[]> sumTokensGroupByUser();

    @Query(value = "SELECT DATE(created_at) as d, COALESCE(SUM(total_tokens), 0) " +
           "FROM ai_token_usage WHERE created_at >= :since GROUP BY DATE(created_at) ORDER BY d", nativeQuery = true)
    List<Object[]> dailyTokenUsage(@Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(a.totalTokens), 0) FROM AiTokenUsage a WHERE a.userId = :userId")
    long sumTotalTokensByUser(@Param("userId") UUID userId);

    // Batched total-token sum keyed by userId — avoids N+1 in admin user listings (one row per user
    // that has usage; users with none are absent and default to 0).
    @Query("SELECT a.userId, COALESCE(SUM(a.totalTokens), 0) FROM AiTokenUsage a " +
           "WHERE a.userId IN :userIds GROUP BY a.userId")
    List<Object[]> sumTotalTokensByUserIn(@Param("userIds") Collection<UUID> userIds);

    @Query("SELECT COALESCE(SUM(a.totalTokens), 0) FROM AiTokenUsage a " +
           "WHERE a.userId = :userId AND a.createdAt >= :since")
    long sumTotalTokensByUserSince(@Param("userId") UUID userId, @Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(a.costMicros), 0) FROM AiTokenUsage a " +
           "WHERE a.userId = :userId AND a.createdAt >= :since")
    long sumCostMicrosByUserSince(@Param("userId") UUID userId, @Param("since") Instant since);

    // Batched per-user AI cost (micros) since a cutoff — avoids N+1 in admin user listings. Each row is
    // [userId, costMicros]; users with no usage in the window are absent and default to 0.
    @Query("SELECT a.userId, COALESCE(SUM(a.costMicros), 0) FROM AiTokenUsage a " +
           "WHERE a.userId IN :userIds AND a.createdAt >= :since GROUP BY a.userId")
    List<Object[]> sumCostMicrosByUserInSince(@Param("userIds") Collection<UUID> userIds,
                                              @Param("since") Instant since);

    // Org-scoped (#4): the monthly AI cost cap + usage widget are billed per-organization.
    @Query("SELECT COALESCE(SUM(a.totalTokens), 0) FROM AiTokenUsage a " +
           "WHERE a.organizationId = :organizationId AND a.createdAt >= :since")
    long sumTotalTokensByOrganizationSince(@Param("organizationId") UUID organizationId, @Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(a.costMicros), 0) FROM AiTokenUsage a " +
           "WHERE a.organizationId = :organizationId AND a.createdAt >= :since")
    long sumCostMicrosByOrganizationSince(@Param("organizationId") UUID organizationId, @Param("since") Instant since);

    // Batched per-org AI cost (micros) since a cutoff — avoids N+1 in the admin quota-overrides list
    // (one row per org with usage in the window; orgs with none are absent and default to 0).
    @Query("SELECT a.organizationId, COALESCE(SUM(a.costMicros), 0) FROM AiTokenUsage a " +
           "WHERE a.organizationId IN :orgIds AND a.createdAt >= :since GROUP BY a.organizationId")
    List<Object[]> sumCostMicrosByOrganizationInSince(@Param("orgIds") Collection<UUID> orgIds,
                                                      @Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(a.costMicros), 0) FROM AiTokenUsage a")
    long sumCostMicros();

    // Global (all-tenant) sums since a cutoff — feed the admin System Health AI-provider metrics.
    @Query("SELECT COALESCE(SUM(a.totalTokens), 0) FROM AiTokenUsage a WHERE a.createdAt >= :since")
    long sumTotalTokensSince(@Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(a.costMicros), 0) FROM AiTokenUsage a WHERE a.createdAt >= :since")
    long sumCostMicrosSince(@Param("since") Instant since);

    @Query("SELECT a.userId, COALESCE(SUM(a.promptTokens), 0), COALESCE(SUM(a.outputTokens), 0), " +
           "COALESCE(SUM(a.totalTokens), 0), COUNT(a), COALESCE(SUM(a.costMicros), 0) " +
           "FROM AiTokenUsage a GROUP BY a.userId ORDER BY SUM(a.totalTokens) DESC")
    List<Object[]> sumTokensAndCostGroupByUser();

    // ── Analytics (#analytics): org-scoped AI cost breakdowns over a time window. ──

    /** AI cost (micros) + tokens grouped by operation for an org since a cutoff: [operation, costMicros, tokens]. */
    @Query("SELECT a.operation, COALESCE(SUM(a.costMicros), 0), COALESCE(SUM(a.totalTokens), 0) " +
           "FROM AiTokenUsage a WHERE a.organizationId = :org AND a.createdAt >= :since " +
           "GROUP BY a.operation ORDER BY SUM(a.costMicros) DESC")
    List<Object[]> costByOperationSince(@Param("org") UUID org, @Param("since") Instant since);

    /** AI cost (micros) + tokens grouped by model for an org since a cutoff: [model, costMicros, tokens]. */
    @Query("SELECT a.model, COALESCE(SUM(a.costMicros), 0), COALESCE(SUM(a.totalTokens), 0) " +
           "FROM AiTokenUsage a WHERE a.organizationId = :org AND a.createdAt >= :since " +
           "GROUP BY a.model ORDER BY SUM(a.costMicros) DESC")
    List<Object[]> costByModelSince(@Param("org") UUID org, @Param("since") Instant since);

    /** Daily AI cost (micros) for an org since a cutoff: [date, costMicros] — daily cost series. */
    @Query(value = "SELECT CAST(created_at AS date) AS d, COALESCE(SUM(cost_micros), 0) " +
           "FROM ai_token_usage WHERE organization_id = :org AND created_at >= :since " +
           "GROUP BY CAST(created_at AS date) ORDER BY d", nativeQuery = true)
    List<Object[]> dailyCostMicros(@Param("org") UUID org, @Param("since") Instant since);

    /** AI cost (micros) for an org in an arbitrary window — period-over-period cost delta. */
    @Query("SELECT COALESCE(SUM(a.costMicros), 0) FROM AiTokenUsage a " +
           "WHERE a.organizationId = :org AND a.createdAt >= :from AND a.createdAt < :to")
    long sumCostMicrosByOrganizationBetween(@Param("org") UUID org, @Param("from") Instant from, @Param("to") Instant to);
}
