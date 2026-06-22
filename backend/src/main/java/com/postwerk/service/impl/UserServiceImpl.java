package com.postwerk.service.impl;

import com.postwerk.dto.*;
import com.postwerk.exception.AuthException;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.*;
import com.postwerk.repository.*;
import com.postwerk.service.AuditService;
import com.postwerk.service.RefreshTokenService;
import com.postwerk.service.UserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default implementation of {@link UserService}.
 *
 * <p>Provides user account management including profile updates, password changes,
 * consent tracking, GDPR-compliant data export, and soft-deletion with token revocation.</p>
 *
 * @since 1.0
 */
@Service
public class UserServiceImpl implements UserService {

    /** Page size for the GDPR export's email read path — bounds memory by streaming in batches. */
    private static final int EXPORT_EMAIL_BATCH_SIZE = 500;

    private final UserRepository userRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final EmailRepository emailRepository;
    private final CategoryRepository categoryRepository;
    private final AuditLogRepository auditLogRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository,
                           EmailAccountRepository emailAccountRepository,
                           EmailRepository emailRepository,
                           CategoryRepository categoryRepository,
                           AuditLogRepository auditLogRepository,
                           RefreshTokenService refreshTokenService,
                           AuditService auditService,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.emailAccountRepository = emailAccountRepository;
        this.emailRepository = emailRepository;
        this.categoryRepository = categoryRepository;
        this.auditLogRepository = auditLogRepository;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = findUser(userId);
        return toProfileResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request, String ipAddress) {
        User user = findUser(userId);

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("fullName", user.getFullName() != null ? user.getFullName() : "");

        user.setFullName(request.fullName());
        user.setCompany(request.company());
        user.setPhone(request.phone());
        userRepository.save(user);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("fullName", user.getFullName() != null ? user.getFullName() : "");

        auditService.logDiff(userId, AuditAction.USER_PROFILE_UPDATED, before, after, null, ipAddress);
        return toProfileResponse(user);
    }

    @Override
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request, String ipAddress) {
        User user = findUser(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AuthException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        auditService.log(userId, AuditAction.USER_PASSWORD_CHANGED, ipAddress);
    }

    @Override
    @Transactional
    public void deleteAccount(UUID userId, String ipAddress) {
        User user = findUser(userId);

        // Soft delete — user data retained for grace period before hard delete
        user.setDeletedAt(Instant.now());
        user.setDeletionReason("USER_REQUESTED");
        userRepository.save(user);

        auditService.log(userId, AuditAction.USER_ACCOUNT_DELETED, ipAddress);
        refreshTokenService.revokeAllForUser(user.getEmail());
    }

    @Override
    @Transactional(readOnly = true)
    public UserExportResponse exportData(UUID userId, String ipAddress) {
        User user = findUser(userId);

        List<EmailAccount> accounts = emailAccountRepository.findByUserId(userId);
        List<UUID> accountIds = accounts.stream().map(EmailAccount::getId).toList();

        // Page through emails in bounded batches with a body-free projection so exporting a large
        // mailbox does not load every email (with full bodies) into memory at once ([L10]).
        List<UserExportResponse.EmailExport> emailExports = new ArrayList<>();
        if (!accountIds.isEmpty()) {
            int pageNumber = 0;
            var pageContent = emailRepository.findByEmailAccountIdInOrderByReceivedAtDesc(
                    accountIds, PageRequest.of(pageNumber, EXPORT_EMAIL_BATCH_SIZE));
            while (true) {
                for (EmailExportView e : pageContent.getContent()) {
                    emailExports.add(new UserExportResponse.EmailExport(
                            e.getId(), e.getEmailAccountId(), e.getFolder(),
                            e.getFromAddress(), e.getToAddresses(),
                            e.getSubject(), e.getSnippet(), e.getReceivedAt(),
                            e.getIsRead(), e.getIsStarred()
                    ));
                }
                if (!pageContent.hasNext()) break;
                pageNumber++;
                pageContent = emailRepository.findByEmailAccountIdInOrderByReceivedAtDesc(
                        accountIds, PageRequest.of(pageNumber, EXPORT_EMAIL_BATCH_SIZE));
            }
        }

        List<Category> categories = categoryRepository.findByUserId(userId);
        List<AuditLog> auditLogs = auditLogRepository.findByUserId(userId);

        var profile = new UserExportResponse.ProfileExport(
                user.getId(), user.getEmail(), user.getFullName(),
                user.getCompany(), user.getPhone(), user.isMarketingOptIn(),
                user.getPrivacyAcceptedAt(), user.getTermsAcceptedAt(),
                user.getPrivacyVersion(), user.getCreatedAt()
        );

        var accountExports = accounts.stream().map(a -> new UserExportResponse.AccountExport(
                a.getId(), a.getEmail(), a.getDisplayName(), a.getColor(),
                a.isReadEnabled(), a.isWriteEnabled(),
                a.getImapHost(), a.getImapPort(),
                a.getSmtpHost(), a.getSmtpPort(),
                a.getCreatedAt()
        )).toList();

        var categoryExports = categories.stream().map(c -> new UserExportResponse.CategoryExport(
                c.getId(), c.getName(), c.getColor(),
                c.getDescription(), c.getCreatedAt()
        )).toList();

        var auditLogExports = auditLogs.stream().map(a -> new UserExportResponse.AuditLogExport(
                a.getId(), a.getAction().name(), a.getDetail(),
                a.getIpAddress(), a.getCreatedAt()
        )).toList();

        auditService.log(userId, AuditAction.USER_DATA_EXPORTED, ipAddress);

        return new UserExportResponse(profile, accountExports, emailExports, categoryExports, List.of(), auditLogExports, Instant.now());
    }

    @Override
    @Transactional
    public void updateConsent(UUID userId, boolean marketingOptIn, String ipAddress) {
        User user = findUser(userId);
        user.setMarketingOptIn(marketingOptIn);
        user.setMarketingOptedInAt(marketingOptIn ? Instant.now() : null);
        userRepository.save(user);

        auditService.log(userId, AuditAction.CONSENT_UPDATED,
                "Marketing opt-in: " + marketingOptIn, ipAddress);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private UserProfileResponse toProfileResponse(User user) {
        return new UserProfileResponse(
                user.getId(), user.getEmail(), user.getFullName(),
                user.getCompany(), user.getPhone(),
                user.getLastLoginAt(), user.getLastLoginIp(),
                user.getPlan() != null ? user.getPlan().getId() : null,
                user.getPlan() != null ? user.getPlan().getName() : null
        );
    }
}
