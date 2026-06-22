package com.postwerk.model;

import com.postwerk.model.enums.NotificationCategory;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A user's delivery preference for one {@link NotificationCategory}. Absence of a row means "use the
 * category default" (in-app on, email off). In-app cannot be turned off for CRITICAL/ACTION_REQUIRED
 * severities — that rule is enforced at send time, not here. See {@code doc/NOTIFICATION_SYSTEM_DESIGN.md}.
 *
 * @since 1.0
 */
@Entity
@Table(name = "notification_preferences", uniqueConstraints = {
        @UniqueConstraint(name = "uq_notif_pref", columnNames = {"user_id", "category"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationCategory category;

    @Column(name = "in_app_enabled", nullable = false)
    @Builder.Default
    private boolean inAppEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    @Builder.Default
    private boolean emailEnabled = false;
}
