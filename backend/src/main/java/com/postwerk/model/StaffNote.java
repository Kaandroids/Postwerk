package com.postwerk.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Internal staff-only note about a customer user (admin panel "Users support tooling").
 *
 * <p>Never shown to the customer. The author's name/email are snapshotted at write time so the
 * note survives the author's account deletion (the {@code authorUserId} FK then nulls out via
 * {@code ON DELETE SET NULL}, while the snapshot fields preserve attribution).</p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "staff_notes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffNote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The customer user this note is about. */
    @Column(name = "target_user_id", nullable = false)
    private UUID targetUserId;

    /** The staff user who wrote the note; nulled out (ON DELETE SET NULL) if that account is deleted. */
    @Column(name = "author_user_id")
    private UUID authorUserId;

    /** Snapshot of the author's name at write time (survives author deletion). */
    @Column(name = "author_name", length = 255)
    private String authorName;

    /** Snapshot of the author's email at write time (survives author deletion). */
    @Column(name = "author_email", length = 255)
    private String authorEmail;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
