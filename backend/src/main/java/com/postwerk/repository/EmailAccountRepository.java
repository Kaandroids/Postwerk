package com.postwerk.repository;

import com.postwerk.model.EmailAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link EmailAccount} entities.
 * Manages user-owned email account configurations including default account resolution and sync-eligible lookups.
 *
 * @since 1.0
 */
public interface EmailAccountRepository extends JpaRepository<EmailAccount, UUID> {

    // Organization-scoped (#4) — mailboxes are owned by the org.
    List<EmailAccount> findByOrganizationId(UUID organizationId);

    Optional<EmailAccount> findByOrganizationIdAndIsDefaultTrue(UUID organizationId);

    Optional<EmailAccount> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndEmail(UUID organizationId, String email);

    long countByOrganizationId(UUID organizationId);

    // User-centric lookups (by connector) — for GDPR export and executor resolution
    // (SendEmailNodeProcessor sender account, WizardServiceImpl). Kept alongside the org methods.
    List<EmailAccount> findByUserId(UUID userId);

    Optional<EmailAccount> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    // Batched count keyed by userId — avoids N+1 in admin user listings (one row per user that
    // owns at least one account; users with zero accounts are absent and default to 0).
    @Query("SELECT e.userId, COUNT(e) FROM EmailAccount e WHERE e.userId IN :userIds GROUP BY e.userId")
    List<Object[]> countByUserIdIn(@Param("userIds") Collection<UUID> userIds);

    // System-wide scheduled sync — every read-enabled IMAP account across all orgs.
    List<EmailAccount> findByReadEnabledTrueAndImapHostIsNotNull();

    // Admin System Health — platform-wide subsystem metric counts (email-sync workers / SMTP).
    long countByReadEnabledTrueAndImapHostIsNotNull();

    long countByWriteEnabledTrueAndSmtpHostIsNotNull();

    long countByLastSyncStatusIn(Collection<String> statuses);

    // Batched mailbox count keyed by orgId — avoids N+1 in the admin subscriptions list.
    @Query("SELECT e.organizationId, COUNT(e) FROM EmailAccount e WHERE e.organizationId IN :orgIds GROUP BY e.organizationId")
    List<Object[]> countByOrganizationIdIn(@Param("orgIds") Collection<UUID> orgIds);
}
