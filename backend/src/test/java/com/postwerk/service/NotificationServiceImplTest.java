package com.postwerk.service;

import com.postwerk.dto.NotificationPreferenceDto;
import com.postwerk.model.Notification;
import com.postwerk.model.NotificationPreference;
import com.postwerk.model.enums.NotificationCategory;
import com.postwerk.model.enums.NotificationType;
import com.postwerk.repository.NotificationPreferenceRepository;
import com.postwerk.repository.NotificationRepository;
import com.postwerk.service.impl.NotificationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository repository;
    @Mock private NotificationPreferenceRepository preferenceRepository;

    private NotificationServiceImpl service;

    private final UUID u1 = UUID.randomUUID();
    private final UUID u2 = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(repository, preferenceRepository, new ObjectMapper());
    }

    private NewNotification spec(NotificationType type, String dedupKey) {
        return new NewNotification(type, orgId, null, null,
                Map.of("automationName", "Invoices"), "/dashboard/approvals", Map.of(), dedupKey);
    }

    @Test
    void create_fansOutOneRowPerRecipient() {
        // APPROVAL_PENDING is ACTION_REQUIRED → forced in-app, no pref lookup; no dedup key.
        service.create(List.of(u1, u2), spec(NotificationType.APPROVAL_PENDING, null));

        verify(repository, times(2)).save(any(Notification.class));
        verify(preferenceRepository, never()).findByUserIdAndCategory(any(), any());
    }

    @Test
    void create_skipsDuplicateRecipientByDedupKey() {
        when(repository.existsByUserIdAndDedupKey(u1, "approval:x")).thenReturn(true);
        when(repository.existsByUserIdAndDedupKey(u2, "approval:x")).thenReturn(false);

        service.create(List.of(u1, u2), spec(NotificationType.APPROVAL_PENDING, "approval:x"));

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(1)).save(cap.capture());
        assertThat(cap.getValue().getUserId()).isEqualTo(u2);
    }

    @Test
    void create_respectsInAppMute_forNonCriticalSeverity() {
        NotificationPreference muted = NotificationPreference.builder()
                .userId(u1).category(NotificationCategory.AUTOMATION).inAppEnabled(false).build();
        when(preferenceRepository.findByUserIdAndCategory(u1, NotificationCategory.AUTOMATION))
                .thenReturn(Optional.of(muted));
        when(preferenceRepository.findByUserIdAndCategory(u2, NotificationCategory.AUTOMATION))
                .thenReturn(Optional.empty()); // default ON

        service.create(List.of(u1, u2), new NewNotification(NotificationType.AUTOMATION_FAILED, orgId,
                null, null, Map.of(), null, Map.of(), null));

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(1)).save(cap.capture());
        assertThat(cap.getValue().getUserId()).isEqualTo(u2);
    }

    @Test
    void create_criticalIgnoresMute() {
        // QUOTA_EXCEEDED is CRITICAL → in-app forced on; preferences must not even be consulted.
        service.create(List.of(u1, u2), new NewNotification(NotificationType.QUOTA_EXCEEDED, orgId,
                null, null, Map.of(), null, Map.of(), null));

        verify(repository, times(2)).save(any(Notification.class));
        verify(preferenceRepository, never()).findByUserIdAndCategory(any(), any());
    }

    @Test
    void getPreferences_returnsFullMatrixWithDefaults() {
        when(preferenceRepository.findByUserId(u1)).thenReturn(List.of());

        List<NotificationPreferenceDto> prefs = service.getPreferences(u1);

        assertThat(prefs).hasSize(NotificationCategory.values().length);
        assertThat(prefs).allSatisfy(p -> {
            assertThat(p.inApp()).isTrue();   // default ON
            assertThat(p.email()).isFalse();  // default OFF
        });
    }

    @Test
    void updatePreferences_upsertsRow() {
        when(preferenceRepository.findByUserIdAndCategory(u1, NotificationCategory.QUOTA))
                .thenReturn(Optional.empty());
        when(preferenceRepository.findByUserId(u1)).thenReturn(List.of());

        service.updatePreferences(u1, List.of(new NotificationPreferenceDto("QUOTA", false, true)));

        ArgumentCaptor<NotificationPreference> cap = ArgumentCaptor.forClass(NotificationPreference.class);
        verify(preferenceRepository).save(cap.capture());
        assertThat(cap.getValue().getCategory()).isEqualTo(NotificationCategory.QUOTA);
        assertThat(cap.getValue().isInAppEnabled()).isFalse();
        assertThat(cap.getValue().isEmailEnabled()).isTrue();
    }

    @Test
    void updatePreferences_rejectsUnknownCategory() {
        assertThatThrownBy(() -> service.updatePreferences(u1,
                List.of(new NotificationPreferenceDto("NOPE", true, true))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
