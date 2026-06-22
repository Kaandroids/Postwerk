package com.postwerk.repository;

import com.postwerk.model.DraftAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link DraftAttachment} entities.
 * Manages file attachments associated with draft emails.
 *
 * @since 1.0
 */
public interface DraftAttachmentRepository extends JpaRepository<DraftAttachment, UUID> {

    List<DraftAttachment> findByEmailId(UUID emailId);

    void deleteByEmailId(UUID emailId);
}
