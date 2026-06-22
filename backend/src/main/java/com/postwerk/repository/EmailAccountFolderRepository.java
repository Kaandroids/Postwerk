package com.postwerk.repository;

import com.postwerk.model.EmailAccountFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link EmailAccountFolder} entities.
 * Manages IMAP folder metadata associated with an email account, ordered by role and name.
 *
 * @since 1.0
 */
public interface EmailAccountFolderRepository extends JpaRepository<EmailAccountFolder, UUID> {

    List<EmailAccountFolder> findByEmailAccountIdOrderByRoleAscNameAsc(UUID emailAccountId);

    Optional<EmailAccountFolder> findByEmailAccountIdAndName(UUID emailAccountId, String name);

    Optional<EmailAccountFolder> findByIdAndEmailAccountId(UUID id, UUID emailAccountId);
}
