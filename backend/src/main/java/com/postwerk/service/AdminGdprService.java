package com.postwerk.service;

import com.postwerk.dto.admin.AdminDataRequestDetailResponse;
import com.postwerk.dto.admin.AdminDataRequestResponse;
import com.postwerk.dto.admin.CreateDataRequestRequest;
import com.postwerk.dto.admin.GdprKpisResponse;
import com.postwerk.dto.admin.RetentionPostureResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Platform-staff GDPR / Data Requests (DSAR) console: the request queue, KPI roll-up, retention
 * posture, per-request detail (footprint + timeline) and the gated mutations (create / run export /
 * execute erasure / reject / mark complete). All mutations are audit-logged; erasure is irreversible.
 *
 * @since 1.0
 */
public interface AdminGdprService {

    Page<AdminDataRequestResponse> listRequests(String search, String type, String status, String deadline,
                                                String sort, String dir, Pageable pageable);

    GdprKpisResponse kpis();

    RetentionPostureResponse retention();

    AdminDataRequestDetailResponse getRequest(UUID id);

    AdminDataRequestResponse create(CreateDataRequestRequest req, UUID actorUserId, String ip);

    AdminDataRequestResponse runExport(UUID id, UUID actorUserId, String ip);

    AdminDataRequestResponse executeErasure(UUID id, UUID actorUserId, String ip);

    AdminDataRequestResponse reject(UUID id, String reason, UUID actorUserId, String ip);

    AdminDataRequestResponse markComplete(UUID id, UUID actorUserId, String ip);
}
