package com.postwerk.repository;

import com.postwerk.model.AiConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AiConversation} entities.
 * Manages user-scoped AI assistant conversation threads, ordered by most recently updated.
 *
 * @since 1.0
 */
public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {

    List<AiConversation> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    List<AiConversation> findTop100ByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<AiConversation> findByIdAndUserId(UUID id, UUID userId);

    /** GDPR data-footprint counts (respect the {@code deleted_at IS NULL} restriction). */
    long countByUserId(UUID userId);

    long countByOrganizationId(UUID organizationId);

    // Org-scoped (#4): a user's conversations within the active organization.
    List<AiConversation> findTop100ByUserIdAndOrganizationIdOrderByUpdatedAtDesc(UUID userId, UUID organizationId);

    Optional<AiConversation> findByIdAndUserIdAndOrganizationId(UUID id, UUID userId, UUID organizationId);

    @Modifying
    @Query(value = "UPDATE ai_conversations SET deleted_at = NOW() WHERE id IN (SELECT id FROM ai_conversations WHERE updated_at < :cutoff AND deleted_at IS NULL LIMIT :batchSize)", nativeQuery = true)
    int softDeleteByUpdatedAtBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);

    @Modifying
    @Query(value = "DELETE FROM ai_conversations WHERE id IN (SELECT id FROM ai_conversations WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff LIMIT :batchSize)", nativeQuery = true)
    int hardDeleteSoftDeletedBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
