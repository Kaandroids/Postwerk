package com.postwerk.model;

import com.postwerk.model.enums.NodeType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a single node in an {@link Automation} workflow graph.
 *
 * <p>Each node has a type ({@link com.postwerk.model.enums.NodeType}), a canvas position
 * for visual layout, an optional label, and a JSONB config object whose schema
 * varies by node type (e.g., category IDs for CATEGORIZE, template ID for REPLY).</p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "automation_nodes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutomationNode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "automation_id", nullable = false)
    private Automation automation;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 50)
    private NodeType nodeType;

    @Column(length = 255)
    private String label;

    /** Stable, friendly per-automation key used for node-scoped variable namespaces
     *  (e.g. {@code http_1}, {@code vectorsearch_2}) instead of the raw UUID. */
    @Column(name = "node_key", length = 40)
    private String nodeKey;

    @Column(name = "position_x", nullable = false)
    private double positionX;

    @Column(name = "position_y", nullable = false)
    private double positionY;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    private String config;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (config == null) config = "{}";
    }
}
