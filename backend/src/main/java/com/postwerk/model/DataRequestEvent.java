package com.postwerk.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One status-change / activity entry in a {@link DataRequest}'s timeline. {@code actor} is the
 * staff display name, or {@code "system"} for automated entries.
 *
 * @since 1.0
 */
@Entity
@Table(name = "data_request_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataRequestEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(nullable = false, length = 500)
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
