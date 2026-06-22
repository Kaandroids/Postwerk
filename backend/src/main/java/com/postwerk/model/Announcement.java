package com.postwerk.model;

import com.postwerk.model.enums.AnnouncementLifecycle;
import com.postwerk.model.enums.AnnouncementPlacement;
import com.postwerk.model.enums.AnnouncementType;
import com.postwerk.model.enums.AudienceScope;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A platform announcement (info / success / warning / maintenance banner) shown to users. Content is
 * bilingual (DE + EN, both required to publish). For a PUBLISHED record the display status is derived
 * from {@code startsAt}/{@code endsAt} (SCHEDULED before the window, EXPIRED after, LIVE within).
 *
 * @since 1.0
 */
@Entity
@Table(name = "announcements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "title_de", nullable = false, length = 200)
    private String titleDe;

    @Column(name = "title_en", nullable = false, length = 200)
    private String titleEn;

    @Column(name = "body_de", columnDefinition = "TEXT")
    private String bodyDe;

    @Column(name = "body_en", columnDefinition = "TEXT")
    private String bodyEn;

    @Column(name = "cta_label_de", length = 120)
    private String ctaLabelDe;

    @Column(name = "cta_label_en", length = 120)
    private String ctaLabelEn;

    @Column(name = "cta_url", length = 2048)
    private String ctaUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnnouncementType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AnnouncementPlacement placement = AnnouncementPlacement.BANNER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AudienceScope audience = AudienceScope.EVERYONE;

    /** CSV of plan names when audience = PLAN (e.g. "PRO,ENTERPRISE"). */
    @Column(name = "audience_plans", length = 200)
    private String audiencePlans;

    @Column(name = "audience_org_id")
    private UUID audienceOrgId;

    @Column(name = "audience_org_name", length = 200)
    private String audienceOrgName;

    @Column(nullable = false)
    @Builder.Default
    private boolean dismissible = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AnnouncementLifecycle lifecycle = AnnouncementLifecycle.DRAFT;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "created_by_name", length = 200)
    private String createdByName;

    @Column(name = "updated_by_name", length = 200)
    private String updatedByName;

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
