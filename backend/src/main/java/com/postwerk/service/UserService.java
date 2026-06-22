package com.postwerk.service;

import com.postwerk.dto.ChangePasswordRequest;
import com.postwerk.dto.UpdateProfileRequest;
import com.postwerk.dto.UserExportResponse;
import com.postwerk.dto.UserProfileResponse;

import java.util.UUID;

/**
 * Service interface for user profile management, including profile updates,
 * password changes, account deletion, GDPR data export, and consent management.
 *
 * @since 1.0
 */
public interface UserService {

    UserProfileResponse getProfile(UUID userId);

    UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request, String ipAddress);

    void changePassword(UUID userId, ChangePasswordRequest request, String ipAddress);

    void deleteAccount(UUID userId, String ipAddress);

    UserExportResponse exportData(UUID userId, String ipAddress);

    void updateConsent(UUID userId, boolean marketingOptIn, String ipAddress);
}
