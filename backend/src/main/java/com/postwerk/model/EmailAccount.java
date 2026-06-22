package com.postwerk.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity representing a user's configured email account (IMAP/SMTP).
 *
 * <p>Stores connection settings for both reading (IMAP) and sending (SMTP),
 * with passwords encrypted at rest via {@link com.postwerk.config.EncryptionConfig}.
 * Each account has a display color and can be marked as the user's default.
 * Soft-deleted via {@code deletedAt} column.</p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "email_accounts", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "email"})
})
@SQLRestriction("deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Owning organization — the scoping key in the multi-tenant model (#4). */
    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(nullable = false)
    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Column(nullable = false, length = 7)
    private String color;

    @Column(name = "read_enabled", nullable = false)
    private boolean readEnabled;

    @Column(name = "write_enabled", nullable = false)
    private boolean writeEnabled;

    @Column(name = "imap_host")
    private String imapHost;

    @Column(name = "imap_port")
    private Integer imapPort;

    @Column(name = "imap_username")
    private String imapUsername;

    @Column(name = "imap_password")
    private String imapPassword;

    @Column(name = "imap_ssl")
    private Boolean imapSsl;

    @Column(name = "smtp_host")
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(name = "smtp_username")
    private String smtpUsername;

    @Column(name = "smtp_password")
    private String smtpPassword;

    @Column(name = "smtp_ssl")
    private Boolean smtpSsl;

    @Column(name = "sync_from_date")
    private LocalDate syncFromDate;

    // ── Sync health (admin Email Health) — written by EmailSyncService on each attempt ──
    /** Timestamp of the last SUCCESSFUL sync (null = never synced). */
    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    /** Outcome of the most recent sync attempt: {@code OK} / {@code AUTH_ERROR} / {@code CONN_ERROR} (null = never synced). */
    @Column(name = "last_sync_status", length = 20)
    private String lastSyncStatus;

    /** Message of the most recent sync failure (null when healthy). */
    @Column(name = "last_error", length = 500)
    private String lastError;

    /** When the current {@code lastError} was first observed. */
    @Column(name = "last_error_at")
    private Instant lastErrorAt;

    /** When true, the scheduled sync skips this mailbox (staff-paused via the admin console). */
    @Column(name = "paused", nullable = false)
    private boolean paused;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        isActive = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
