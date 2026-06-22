package com.postwerk.service;

import com.postwerk.dto.admin.AdminAnnouncementDetailResponse;
import com.postwerk.dto.admin.AdminAnnouncementResponse;
import com.postwerk.dto.admin.AnnouncementKpisResponse;
import com.postwerk.dto.admin.AnnouncementRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Platform-staff Announcements console: the announcement queue, lifecycle KPIs, per-record detail
 * (+ change history) and the gated mutations (create / save / publish / end / archive / duplicate).
 * Read + mutate both gate on {@code ANNOUNCEMENT_MANAGE}; every mutation is audit-logged.
 *
 * @since 1.0
 */
public interface AdminAnnouncementService {

    Page<AdminAnnouncementResponse> list(String search, String type, String status, String audience,
                                         String placement, String sort, Pageable pageable);

    AnnouncementKpisResponse kpis();

    AdminAnnouncementDetailResponse getAnnouncement(UUID id);

    AdminAnnouncementResponse create(AnnouncementRequest req, UUID actorUserId, String ip);

    AdminAnnouncementResponse update(UUID id, AnnouncementRequest req, UUID actorUserId, String ip);

    AdminAnnouncementResponse publish(UUID id, UUID actorUserId, String ip);

    AdminAnnouncementResponse end(UUID id, UUID actorUserId, String ip);

    AdminAnnouncementResponse archive(UUID id, UUID actorUserId, String ip);

    AdminAnnouncementResponse duplicate(UUID id, UUID actorUserId, String ip);
}
