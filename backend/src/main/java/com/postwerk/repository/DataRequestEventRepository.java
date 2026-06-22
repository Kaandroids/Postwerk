package com.postwerk.repository;

import com.postwerk.model.DataRequestEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for {@link DataRequestEvent} timeline entries of a DSAR.
 *
 * @since 1.0
 */
public interface DataRequestEventRepository extends JpaRepository<DataRequestEvent, UUID> {

    List<DataRequestEvent> findByRequestIdOrderByCreatedAtAsc(UUID requestId);
}
