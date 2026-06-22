package com.postwerk.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity granting a {@link Membership} read/send access to a specific mailbox
 * ({@link EmailAccount}) — the per-mailbox access layer of the multi-tenant model (#4).
 *
 * <p>Owner/Admin roles bypass grants (implicit all-mailbox access); Member/Viewer need an explicit
 * grant row per mailbox. This is what lets an intern access {@code support@} but not {@code ceo@}.
 * Unique per (membership, mailbox).</p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "mailbox_grants", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"membership_id", "mailbox_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailboxGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "membership_id", nullable = false)
    private UUID membershipId;

    @Column(name = "mailbox_id", nullable = false)
    private UUID mailboxId;

    @Column(name = "can_read", nullable = false)
    @Builder.Default
    private boolean canRead = true;

    @Column(name = "can_send", nullable = false)
    @Builder.Default
    private boolean canSend = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
