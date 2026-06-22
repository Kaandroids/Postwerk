package com.postwerk.service;

import com.postwerk.TestFixtures;
import com.postwerk.dto.ChangePasswordRequest;
import com.postwerk.dto.UpdateProfileRequest;
import com.postwerk.dto.UserProfileResponse;
import com.postwerk.exception.AuthException;
import com.postwerk.model.AuditAction;
import com.postwerk.model.User;
import com.postwerk.repository.AuditLogRepository;
import com.postwerk.repository.CategoryRepository;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.EmailRepository;
import com.postwerk.repository.UserRepository;
import com.postwerk.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private EmailAccountRepository emailAccountRepository;
    @Mock private EmailRepository emailRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private AuditService auditService;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        testUser = TestFixtures.createUser();
        userId = testUser.getId();
    }

    @Test
    void changePassword_correctOld_succeeds() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldPass", testUser.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("newPass123!")).thenReturn("encoded-new");

        var request = new ChangePasswordRequest("oldPass", "newPass123!");
        userService.changePassword(userId, request, "127.0.0.1");

        verify(userRepository).save(argThat(u -> u.getPasswordHash().equals("encoded-new")));
        verify(auditService).log(eq(userId), eq(AuditAction.USER_PASSWORD_CHANGED), anyString());
    }

    @Test
    void changePassword_wrongOld_throwsAuthException() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPass", testUser.getPasswordHash())).thenReturn(false);

        var request = new ChangePasswordRequest("wrongPass", "newPass123!");
        assertThatThrownBy(() -> userService.changePassword(userId, request, "127.0.0.1"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Current password is incorrect");
    }

    @Test
    void deleteAccount_softDeletes_revokesTokens() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        userService.deleteAccount(userId, "127.0.0.1");

        assertThat(testUser.getDeletedAt()).isNotNull();
        assertThat(testUser.getDeletionReason()).isEqualTo("USER_REQUESTED");
        verify(refreshTokenService).revokeAllForUser(testUser.getEmail());
        verify(auditService).log(eq(userId), eq(AuditAction.USER_ACCOUNT_DELETED), anyString());
    }

    @Test
    void exportData_returnsAllEntities() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(emailAccountRepository.findByUserId(userId)).thenReturn(List.of());
        when(categoryRepository.findByUserId(userId)).thenReturn(List.of());
        when(auditLogRepository.findByUserId(userId)).thenReturn(List.of());

        var response = userService.exportData(userId, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.profile().email()).isEqualTo(testUser.getEmail());
        assertThat(response.exportedAt()).isNotNull();
        verify(auditService).log(eq(userId), eq(AuditAction.USER_DATA_EXPORTED), anyString());
    }

    @Test
    void updateProfile_validData_auditsChanges() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        var request = new UpdateProfileRequest("New Name", "New Corp", "+49111222333");
        UserProfileResponse response = userService.updateProfile(userId, request, "127.0.0.1");

        assertThat(response).isNotNull();
        verify(auditService).logDiff(eq(userId), any(), any(), any(), any(), anyString());
    }

    @Test
    void getProfile_nonExistentUser_throws() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void updateConsent_setsTimestamp() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        userService.updateConsent(userId, true, "127.0.0.1");

        assertThat(testUser.isMarketingOptIn()).isTrue();
        assertThat(testUser.getMarketingOptedInAt()).isNotNull();
        verify(userRepository).save(testUser);
    }

    @Test
    void updateConsent_optOut_clearsTimestamp() {
        testUser.setMarketingOptIn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        userService.updateConsent(userId, false, "127.0.0.1");

        assertThat(testUser.isMarketingOptIn()).isFalse();
        assertThat(testUser.getMarketingOptedInAt()).isNull();
    }
}
