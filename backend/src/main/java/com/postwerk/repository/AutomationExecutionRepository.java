package com.postwerk.repository;

import com.postwerk.model.AutomationExecution;
import com.postwerk.model.enums.ExecutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AutomationExecution} entities.
 * Provides paginated access to automation execution history, ordered by trigger timestamp.
 *
 * @since 1.0
 */
public interface AutomationExecutionRepository extends JpaRepository<AutomationExecution, UUID> {

    Page<AutomationExecution> findByAutomationIdOrderByTriggeredAtDesc(UUID automationId, Pageable pageable);

    long countByStatus(ExecutionStatus status);

    Page<AutomationExecution> findAllByOrderByTriggeredAtDesc(Pageable pageable);

    @Query(value = "SELECT DATE(triggered_at) as d, COUNT(*) " +
           "FROM automation_executions WHERE triggered_at >= :since GROUP BY DATE(triggered_at) ORDER BY d", nativeQuery = true)
    List<Object[]> dailyExecutionCount(@Param("since") Instant since);

    @Query(value = "SELECT automation_id, COUNT(*) as total, " +
           "SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) as success_count, " +
           "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed_count " +
           "FROM automation_executions GROUP BY automation_id ORDER BY total DESC LIMIT 10", nativeQuery = true)
    List<Object[]> topAutomationsByExecutionCount();

    @Query(value = "SELECT e.automation_id, COUNT(*) as total, " +
           "SUM(CASE WHEN e.status = 'SUCCESS' THEN 1 ELSE 0 END) as success_count, " +
           "SUM(CASE WHEN e.status = 'FAILED' THEN 1 ELSE 0 END) as failed_count " +
           "FROM automation_executions e WHERE e.automation_id IN :ids GROUP BY e.automation_id",
           nativeQuery = true)
    List<Object[]> statsForAutomations(@Param("ids") List<UUID> ids);
}
