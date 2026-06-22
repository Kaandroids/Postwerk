package com.postwerk.repository;

import com.postwerk.model.AutomationDelayedEmail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AutomationDelayedEmail} entities.
 * Retrieves unprocessed delayed emails whose delay period has expired for deferred execution.
 *
 * @since 1.0
 */
public interface AutomationDelayedEmailRepository extends JpaRepository<AutomationDelayedEmail, UUID> {

    List<AutomationDelayedEmail> findByProcessedFalseAndDelayedUntilBefore(Instant now);

    /** Count of not-yet-processed delayed emails — feeds the admin System Health "job queue depth". */
    long countByProcessedFalse();
}
