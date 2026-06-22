package com.postwerk.repository;

import com.postwerk.model.AutomationNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AutomationNode} entities.
 * Handles persistence and bulk deletion of individual nodes within an automation workflow graph.
 *
 * @since 1.0
 */
public interface AutomationNodeRepository extends JpaRepository<AutomationNode, UUID> {

    List<AutomationNode> findByAutomationId(UUID automationId);

    // Batched node load for a set of automations — avoids N+1 when exporting/loading several
    // automations at once. Callers group the result by automation id in memory.
    List<AutomationNode> findByAutomationIdIn(Collection<UUID> automationIds);

    int countByAutomationId(UUID automationId);

    @Query("SELECT n.automation.id, COUNT(n) FROM AutomationNode n WHERE n.automation.id IN :ids GROUP BY n.automation.id")
    List<Object[]> countByAutomationIds(List<UUID> ids);

    // Batched TRIGGER-node config load — lets the list endpoint expose each automation's trigger mode
    // (e.g. to gate the manual "Run now" action) without loading the whole node graph.
    @Query("SELECT n.automation.id, n.config FROM AutomationNode n WHERE n.automation.id IN :ids AND n.nodeType = com.postwerk.model.enums.NodeType.TRIGGER")
    List<Object[]> findTriggerConfigsByAutomationIds(List<UUID> ids);

    void deleteByAutomationId(UUID automationId);
}
