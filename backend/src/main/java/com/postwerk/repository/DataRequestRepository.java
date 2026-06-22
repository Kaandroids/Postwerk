package com.postwerk.repository;

import com.postwerk.model.DataRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for the DSAR queue. The admin Compliance console filters/sorts/paginates in-memory
 * over {@link #findAllByOrderByRequestedAtDesc()} (DSAR volume is low), mirroring the other admin
 * surfaces, and computes KPIs from the same set.
 *
 * @since 1.0
 */
public interface DataRequestRepository extends JpaRepository<DataRequest, UUID> {

    List<DataRequest> findAllByOrderByRequestedAtDesc();
}
