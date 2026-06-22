package com.postwerk.model;

import com.postwerk.model.enums.NodeResultStatus;
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
 * JPA entity recording the result of a single node execution within an automation trace.
 *
 * <p>Each entry captures the node type, label, execution order, result status (SUCCESS/FAILED/SKIPPED),
 * and a JSONB detail payload with node-specific output (e.g., classification confidence,
 * extracted parameters, filter match results).</p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "email_node_traces")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailNodeTrace {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trace_id", nullable = false)
    private EmailAutomationTrace trace;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 50)
    private NodeType nodeType;

    @Column(name = "node_label")
    private String nodeLabel;

    @Column(name = "execution_order", nullable = false)
    private int executionOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false, length = 20)
    private NodeResultStatus resultStatus;

    @Column(name = "result_detail", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String resultDetail;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @PrePersist
    protected void onCreate() {
        if (executedAt == null) executedAt = Instant.now();
    }
}
