package com.postwerk.service;

import com.postwerk.dto.admin.AdminFlagDetailResponse;
import com.postwerk.dto.admin.AdminFlagResponse;
import com.postwerk.dto.admin.CreateFlagRequest;
import com.postwerk.dto.admin.FeatureFlagKpisResponse;
import com.postwerk.dto.admin.UpdateFlagRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Platform-staff Feature Flags console: the flag list, definition/state/staleness KPIs, per-flag
 * detail (+ change history + overrides) and the gated mutations (create / save / enable / disable /
 * kill / restore / archive / duplicate). Read + mutate gate on {@code FEATURE_FLAG_MANAGE}; every
 * mutation is audit-logged.
 *
 * @since 1.0
 */
public interface AdminFeatureFlagService {

    Page<AdminFlagResponse> list(String search, String kind, String status, String targeting,
                                 String health, String sort, Pageable pageable);

    FeatureFlagKpisResponse kpis();

    AdminFlagDetailResponse getFlag(UUID id);

    AdminFlagResponse create(CreateFlagRequest req, UUID actorUserId, String ip);

    AdminFlagResponse update(UUID id, UpdateFlagRequest req, UUID actorUserId, String ip);

    AdminFlagResponse setEnabled(UUID id, boolean enabled, UUID actorUserId, String ip);

    AdminFlagResponse kill(UUID id, UUID actorUserId, String ip);

    AdminFlagResponse restore(UUID id, UUID actorUserId, String ip);

    AdminFlagResponse archive(UUID id, UUID actorUserId, String ip);

    AdminFlagResponse duplicate(UUID id, UUID actorUserId, String ip);
}
