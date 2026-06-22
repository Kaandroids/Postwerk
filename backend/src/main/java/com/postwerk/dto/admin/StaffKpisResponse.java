package com.postwerk.dto.admin;

/**
 * KPI roll-up for the Staff &amp; Roles console — roster/role counts only. {@code superAdmins == 1}
 * is a single-point-of-failure risk surfaced by the UI.
 *
 * @since 1.0
 */
public record StaffKpisResponse(
        long total,
        long superAdmins,
        long privileged,
        long readOnly,
        long added30d
) {}
