package com.postwerk.repository;

import com.postwerk.model.ModelPricing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ModelPricing} (admin-editable per-model AI rates).
 *
 * @since 1.0
 */
public interface ModelPricingRepository extends JpaRepository<ModelPricing, UUID> {

    Optional<ModelPricing> findByModel(String model);

    boolean existsByModel(String model);
}
