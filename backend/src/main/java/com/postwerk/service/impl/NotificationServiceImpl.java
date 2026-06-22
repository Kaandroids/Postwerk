package com.postwerk.service.impl;

import com.postwerk.dto.NotificationListResponse;
import com.postwerk.dto.NotificationPreferenceDto;
import com.postwerk.dto.NotificationResponse;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.Notification;
import com.postwerk.model.NotificationPreference;
import com.postwerk.model.enums.NotificationCategory;
import com.postwerk.model.enums.NotificationSeverity;
import com.postwerk.model.enums.NotificationType;
import com.postwerk.repository.NotificationPreferenceRepository;
import com.postwerk.repository.NotificationRepository;
import com.postwerk.service.NewNotification;
import com.postwerk.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default {@link NotificationService}. The DB is the source of truth; this writes one row per
 * recipient (the in-app channel). Per-recipient dedup and the in-app preference gate are applied
 * here; in-app cannot be disabled for CRITICAL/ACTION_REQUIRED. See {@code doc/NOTIFICATION_SYSTEM_DESIGN.md}.
 *
 * @since 1.0
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository repository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final ObjectMapper objectMapper;

    public NotificationServiceImpl(NotificationRepository repository,
                                   NotificationPreferenceRepository preferenceRepository,
                                   ObjectMapper objectMapper) {
        this.repository = repository;
        this.preferenceRepository = preferenceRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void create(Collection<UUID> recipientUserIds, NewNotification spec) {
        if (recipientUserIds == null || recipientUserIds.isEmpty()) return;

        NotificationType type = spec.type();
        NotificationCategory category = spec.categoryOverride() != null ? spec.categoryOverride() : type.getCategory();
        NotificationSeverity severity = spec.severityOverride() != null ? spec.severityOverride() : type.getDefaultSeverity();
        boolean forcedInApp = severity == NotificationSeverity.CRITICAL || severity == NotificationSeverity.ACTION_REQUIRED;
        String paramsJson = toJson(spec.params());
        String payloadJson = toJson(spec.payload());

        for (UUID userId : new LinkedHashSet<>(recipientUserIds)) {
            if (userId == null) continue;
            if (spec.dedupKey() != null && repository.existsByUserIdAndDedupKey(userId, spec.dedupKey())) {
                continue; // already delivered to this recipient
            }
            if (!forcedInApp && !inAppEnabled(userId, category)) {
                continue; // user muted in-app for this category (non-critical)
            }
            repository.save(Notification.builder()
                    .userId(userId)
                    .organizationId(spec.organizationId())
                    .category(category)
                    .type(type)
                    .severity(severity)
                    .titleKey(type.getTitleKey())
                    .bodyKey(type.getBodyKey())
                    .params(paramsJson)
                    .linkUrl(spec.linkUrl())
                    .payload(payloadJson)
                    .dedupKey(spec.dedupKey())
                    .build());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationListResponse list(UUID userId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? repository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable)
                : repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        List<NotificationResponse> items = page.getContent().stream().map(this::toResponse).toList();
        return new NotificationListResponse(items, repository.countByUserIdAndReadAtIsNull(userId), page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Override
    @Transactional
    public void markRead(UUID userId, UUID id) {
        Notification n = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
            repository.save(n);
        }
    }

    @Override
    @Transactional
    public void markAllRead(UUID userId) {
        repository.markAllRead(userId, Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationPreferenceDto> getPreferences(UUID userId) {
        Map<NotificationCategory, NotificationPreference> stored = preferenceRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(NotificationPreference::getCategory, p -> p, (a, b) -> a));
        List<NotificationPreferenceDto> out = new ArrayList<>();
        for (NotificationCategory category : NotificationCategory.values()) {
            NotificationPreference p = stored.get(category);
            boolean inApp = p == null || p.isInAppEnabled();   // default ON
            boolean email = p != null && p.isEmailEnabled();   // default OFF
            out.add(new NotificationPreferenceDto(category.name(), inApp, email));
        }
        return out;
    }

    @Override
    @Transactional
    public List<NotificationPreferenceDto> updatePreferences(UUID userId, List<NotificationPreferenceDto> preferences) {
        if (preferences != null) {
            for (NotificationPreferenceDto dto : preferences) {
                NotificationCategory category = parseCategory(dto.category());
                NotificationPreference row = preferenceRepository.findByUserIdAndCategory(userId, category)
                        .orElseGet(() -> NotificationPreference.builder().userId(userId).category(category).build());
                row.setInAppEnabled(dto.inApp());
                row.setEmailEnabled(dto.email());
                preferenceRepository.save(row);
            }
        }
        return getPreferences(userId);
    }

    private boolean inAppEnabled(UUID userId, NotificationCategory category) {
        return preferenceRepository.findByUserIdAndCategory(userId, category)
                .map(NotificationPreference::isInAppEnabled)
                .orElse(true); // default ON when no row
    }

    private NotificationCategory parseCategory(String value) {
        try {
            return NotificationCategory.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unknown notification category: " + value);
        }
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getCategory() != null ? n.getCategory().name() : null,
                n.getType() != null ? n.getType().name() : null,
                n.getSeverity() != null ? n.getSeverity().name() : null,
                n.getTitleKey(),
                n.getBodyKey(),
                parseMap(n.getParams()),
                n.getLinkUrl(),
                parseMap(n.getPayload()),
                n.getOrganizationId(),
                n.getReadAt() != null,
                n.getCreatedAt());
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map != null ? map : Map.of());
        } catch (Exception e) {
            log.warn("Failed to serialize notification JSON: {}", e.getMessage());
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
