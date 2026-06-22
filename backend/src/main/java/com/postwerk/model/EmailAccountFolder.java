package com.postwerk.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing an IMAP folder discovered for an {@link EmailAccount}.
 *
 * <p>Stores the folder name, its functional role (INBOX, SENT, TRASH, OTHER),
 * message/unread counts, and the last sync timestamp. Populated during IMAP
 * folder synchronization.</p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "email_account_folders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailAccountFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "email_account_id", nullable = false)
    private UUID emailAccountId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Builder.Default
    private String role = "OTHER";

    @Column(name = "message_count")
    @Builder.Default
    private Integer messageCount = 0;

    @Column(name = "unread_count")
    @Builder.Default
    private Integer unreadCount = 0;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;
}
