package com.postwerk.dto.admin;

import java.util.List;

/**
 * The full role→capability matrix: every staff role (privileged-first) with its bundle, plus the
 * complete capability vocabulary ({@code allPermissions}) so the UI can render granted/not-granted.
 *
 * @since 1.0
 */
public record StaffRolesMatrixResponse(
        List<StaffRoleInfoResponse> roles,
        List<String> allPermissions
) {}
