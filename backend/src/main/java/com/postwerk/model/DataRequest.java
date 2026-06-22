package com.postwerk.model;

import com.postwerk.model.enums.DataRequestChannel;
import com.postwerk.model.enums.DataRequestStatus;
import com.postwerk.model.enums.DataRequestType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A data-subject access request (DSAR) handled by compliance staff against the statutory 30-day
 * GDPR/DSGVO deadline. The subject may or may not resolve to a known {@link User}
 * ({@code subjectUserId} null = unmatched requester). {@code deadlineAt} is derived at creation
 * ({@code requestedAt + 30 days}).
 *
 * @since 1.0
 */
@Entity
@Table(name = "data_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Resolved data subject, or null if the email matches no account. */
    @Column(name = "subject_user_id")
    private UUID subjectUserId;

    @Column(name = "subject_name", nullable = false, length = 200)
    private String subjectName;

    @Column(name = "subject_email", nullable = false, length = 320)
    private String subjectEmail;

    /** Subject's personal org (footprint scope), or null. */
    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "org_name", length = 200)
    private String orgName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DataRequestType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DataRequestStatus status = DataRequestStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DataRequestChannel channel = DataRequestChannel.EMAIL;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "handler_user_id")
    private UUID handlerUserId;

    @Column(name = "handler_name", length = 200)
    private String handlerName;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
