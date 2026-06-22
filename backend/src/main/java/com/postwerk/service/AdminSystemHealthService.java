package com.postwerk.service;

import com.postwerk.dto.admin.MaintenanceModeResponse;
import com.postwerk.dto.admin.SubsystemHealthResponse;
import com.postwerk.dto.admin.SystemHealthEventResponse;
import com.postwerk.dto.admin.SystemHealthKpisResponse;

import java.util.List;
import java.util.UUID;

/**
 * Platform-staff System Health: live probes of Postwerk's own subsystems (API, PostgreSQL, Redis,
 * job scheduler, email-sync workers, SMTP, AI provider), plus gated re-probe / cache-flush /
 * maintenance-mode actions. Reads need {@code INFRA_VIEW}; mutations need {@code INFRA_MANAGE}.
 *
 * @since 1.0
 */
public interface AdminSystemHealthService {

    List<SubsystemHealthResponse> subsystems();

    SystemHealthKpisResponse kpis();

    List<SystemHealthEventResponse> events();

    /** Re-probes one subsystem by id; throws if the id is unknown. */
    SubsystemHealthResponse getSubsystem(String id);

    SubsystemHealthResponse probe(String id, UUID actorUserId, String ip);

    /** Clears the application cache regions (NOT the whole Redis instance). */
    void flushCache(UUID actorUserId, String ip);

    MaintenanceModeResponse getMaintenance();

    MaintenanceModeResponse setMaintenance(boolean enabled, String message, UUID actorUserId, String ip);
}
