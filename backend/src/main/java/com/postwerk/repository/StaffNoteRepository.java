package com.postwerk.repository;

import com.postwerk.model.StaffNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link StaffNote} (internal staff-only notes about a user).
 *
 * @since 1.0
 */
public interface StaffNoteRepository extends JpaRepository<StaffNote, UUID> {

    /** All notes about the given target user, newest first (covered by idx_staff_notes_target). */
    List<StaffNote> findByTargetUserIdOrderByCreatedAtDesc(UUID targetUserId);
}
