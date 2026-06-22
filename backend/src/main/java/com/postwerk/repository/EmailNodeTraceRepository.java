package com.postwerk.repository;

import com.postwerk.model.EmailNodeTrace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link EmailNodeTrace} entities.
 * Retrieves per-node execution traces within an automation run, ordered by execution sequence.
 *
 * @since 1.0
 */
public interface EmailNodeTraceRepository extends JpaRepository<EmailNodeTrace, UUID> {

    List<EmailNodeTrace> findByTraceIdOrderByExecutionOrder(UUID traceId);
}
