package com.postwerk.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry in an {@link Announcement}'s change history (created / published / ended / archived /
 * edited). {@code actor} is the staff display name, or {@code "system"}.
 *
 * @since 1.0
 */
@Entity
@Table(name = "announcement_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "announcement_id", nullable = false)
    private UUID announcementId;

    @Column(nullable = false, length = 300)
    private String label;

    @Column(nullable = false, length = 200)
    @Builder.Default
    private String actor = "system";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
