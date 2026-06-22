package com.postwerk.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a directed connection between two {@link AutomationNode}s.
 *
 * <p>Edges define the data flow in an automation workflow. Each edge connects a
 * source node's output handle to a target node's input handle, enabling
 * conditional routing (e.g., category_0 output to a specific action node).</p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "automation_edges")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutomationEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "automation_id", nullable = false)
    private Automation automation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_node_id", nullable = false)
    private AutomationNode sourceNode;

    @Column(name = "source_handle", nullable = false, length = 50)
    private String sourceHandle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_node_id", nullable = false)
    private AutomationNode targetNode;

    @Column(name = "target_handle", nullable = false, length = 50)
    private String targetHandle;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (sourceHandle == null) sourceHandle = "output";
        if (targetHandle == null) targetHandle = "input";
    }
}
