package com.postwerk.repository;

import com.postwerk.model.MailboxGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link MailboxGrant} entities (multi-tenant model #4).
 *
 * @since 1.0
 */
public interface MailboxGrantRepository extends JpaRepository<MailboxGrant, UUID> {

    /** All mailbox grants for a membership (the inboxes a member can read/send). */
    List<MailboxGrant> findByMembershipId(UUID membershipId);

    Optional<MailboxGrant> findByMembershipIdAndMailboxId(UUID membershipId, UUID mailboxId);

    void deleteByMembershipId(UUID membershipId);

    void deleteByMailboxId(UUID mailboxId);
}
