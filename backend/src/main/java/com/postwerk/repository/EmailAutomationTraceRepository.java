package com.postwerk.repository;

import com.postwerk.model.EmailAutomationTrace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link EmailAutomationTrace} entities.
 * Tracks automation execution history per email, supporting trace lookup, counting, and cascading deletion.
 *
 * @since 1.0
 */
public interface EmailAutomationTraceRepository extends JpaRepository<EmailAutomationTrace, UUID> {

    List<EmailAutomationTrace> findByEmailIdOrderByStartedAtDesc(UUID emailId);

    List<EmailAutomationTrace> findByEmailIdAndAutomationId(UUID emailId, UUID automationId);

    /** Recent live-run traces across a set of automations (the user's), newest first — the activity feed (#3d).
     *  Eager-fetches the ToOne {@code email} (pagination-safe) to avoid per-row N+1; the {@code nodeTraces}
     *  collection is left lazy and batched via {@code @BatchSize} on the entity. */
    @EntityGraph(attributePaths = {"email"})
    Page<EmailAutomationTrace> findByAutomationIdInOrderByStartedAtDesc(Collection<UUID> automationIds, Pageable pageable);

    boolean existsByEmailIdAndAutomationId(UUID emailId, UUID automationId);

    int countByEmailId(UUID emailId);

    /** Batch trace counts for a set of emails — avoids the inbox-list N+1 (one count per row). */
    @Query("SELECT t.email.id, COUNT(t) FROM EmailAutomationTrace t WHERE t.email.id IN :ids GROUP BY t.email.id")
    List<Object[]> countByEmailIdIn(Collection<UUID> ids);

    @Modifying
    void deleteByEmailId(UUID emailId);

    // ── Analytics (#analytics): org-wide and per-automation aggregations over live traces. ──
    // All exclude simulation (TESTING) traces; status is the TraceStatus name (RUNNING/SUCCESS/FAILED).

    /** Daily [date, status, count] buckets for a set of automations — execution trend + KPI series. */
    @Query(value = "SELECT CAST(started_at AS date) AS d, status, COUNT(*) " +
           "FROM email_automation_traces " +
           "WHERE automation_id IN (:ids) AND started_at >= :since AND simulation = false " +
           "GROUP BY CAST(started_at AS date), status", nativeQuery = true)
    List<Object[]> dailyStatusCounts(@Param("ids") Collection<UUID> ids, @Param("since") Instant since);

    /** Per-automation [automationId, total, failed, lastRun] in the window — top automations. */
    @Query(value = "SELECT automation_id, COUNT(*) AS total, " +
           "COUNT(*) FILTER (WHERE status = 'FAILED') AS failed, MAX(started_at) AS last_run " +
           "FROM email_automation_traces " +
           "WHERE automation_id IN (:ids) AND started_at >= :since AND simulation = false " +
           "GROUP BY automation_id", nativeQuery = true)
    List<Object[]> automationStats(@Param("ids") Collection<UUID> ids, @Param("since") Instant since);

    /** Distinct emails touched by a set of automations in the window — emails-processed KPI. */
    @Query(value = "SELECT COUNT(DISTINCT email_id) FROM email_automation_traces " +
           "WHERE automation_id IN (:ids) AND started_at >= :since AND simulation = false AND email_id IS NOT NULL",
           nativeQuery = true)
    long countDistinctEmails(@Param("ids") Collection<UUID> ids, @Param("since") Instant since);

    /** Total live runs across a set of automations in an arbitrary window — period-over-period deltas. */
    @Query(value = "SELECT COUNT(*) FROM email_automation_traces " +
           "WHERE automation_id IN (:ids) AND started_at >= :from AND started_at < :to AND simulation = false",
           nativeQuery = true)
    long countRunsBetween(@Param("ids") Collection<UUID> ids, @Param("from") Instant from, @Param("to") Instant to);

    /** Failure analysis by node type across a set of automations: [nodeType, failures, total]. */
    @Query(value = "SELECT n.node_type, " +
           "COUNT(*) FILTER (WHERE n.result_status = 'FAILED') AS failures, COUNT(*) AS total " +
           "FROM email_node_traces n JOIN email_automation_traces t ON n.trace_id = t.id " +
           "WHERE t.automation_id IN (:ids) AND t.started_at >= :since AND t.simulation = false " +
           "GROUP BY n.node_type", nativeQuery = true)
    List<Object[]> failuresByNodeType(@Param("ids") Collection<UUID> ids, @Param("since") Instant since);

    // ── per-automation (drill-down) variants ──

    /** Daily [date, status, count] buckets for one automation — detail execution trend. */
    @Query(value = "SELECT CAST(started_at AS date) AS d, status, COUNT(*) " +
           "FROM email_automation_traces " +
           "WHERE automation_id = :automationId AND started_at >= :since AND simulation = false " +
           "GROUP BY CAST(started_at AS date), status", nativeQuery = true)
    List<Object[]> dailyStatusCountsForAutomation(@Param("automationId") UUID automationId, @Param("since") Instant since);

    /** Per-node failures for one automation: [nodeId, nodeLabel, nodeType, failures, total]. */
    @Query(value = "SELECT n.node_id, MAX(n.node_label), n.node_type, " +
           "COUNT(*) FILTER (WHERE n.result_status = 'FAILED') AS failures, COUNT(*) AS total " +
           "FROM email_node_traces n JOIN email_automation_traces t ON n.trace_id = t.id " +
           "WHERE t.automation_id = :automationId AND t.started_at >= :since AND t.simulation = false " +
           "GROUP BY n.node_id, n.node_type", nativeQuery = true)
    List<Object[]> nodeFailuresForAutomation(@Param("automationId") UUID automationId, @Param("since") Instant since);

    /** Distinct emails touched by one automation in the window. */
    @Query(value = "SELECT COUNT(DISTINCT email_id) FROM email_automation_traces " +
           "WHERE automation_id = :automationId AND started_at >= :since AND simulation = false AND email_id IS NOT NULL",
           nativeQuery = true)
    long countDistinctEmailsForAutomation(@Param("automationId") UUID automationId, @Param("since") Instant since);

    /** Recent live runs for one automation (newest first) — drill-down recent-runs table. */
    @EntityGraph(attributePaths = {"email"})
    Page<EmailAutomationTrace> findByAutomationIdAndSimulationFalseOrderByStartedAtDesc(UUID automationId, Pageable pageable);
}
