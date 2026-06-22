package com.postwerk.dto.admin;

import java.util.List;

/**
 * The signed-in staff member's own identity and effective platform capabilities. Consumed by the
 * admin UI to gate navigation groups and per-action controls (e.g. show a lock when a capability
 * is absent). {@code staffRole} is {@code null} when the caller is not platform staff.
 */
public record StaffIdentityResponse(
        String email,
        String role,
        String staffRole,
        List<String> permissions
) {}
