package com.postwerk.repository;

import com.postwerk.model.AutomationEdge;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AutomationEdge} entities.
 * Manages the directed connections between nodes in an automation workflow graph.
 *
 * @since 1.0
 */
public interface AutomationEdgeRepository extends JpaRepository<AutomationEdge, UUID> {

    @EntityGraph(attributePaths = {"sourceNode", "targetNode"})
    List<AutomationEdge> findByAutomationId(UUID automationId);

    // Batched edge load for a set of automations — avoids N+1 when exporting/loading several
    // automations at once. Keeps the source/target node fetch graph so callers can read node ids
    // without lazy-loading per edge. Callers group the result by automation id in memory.
    @EntityGraph(attributePaths = {"sourceNode", "targetNode"})
    List<AutomationEdge> findByAutomationIdIn(Collection<UUID> automationIds);

    int countByAutomationId(UUID automationId);

    @Query("SELECT e.automation.id, COUNT(e) FROM AutomationEdge e WHERE e.automation.id IN :ids GROUP BY e.automation.id")
    List<Object[]> countByAutomationIds(List<UUID> ids);

    void deleteByAutomationId(UUID automationId);
}
