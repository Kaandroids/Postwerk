package com.postwerk.dto.admin;

import java.util.List;

/**
 * One staff role and its fixed capability bundle — for the read-only roles reference + capability
 * matrix. {@code permissions} are the real {@code StaffRole.permissions()} names.
 *
 * @since 1.0
 */
public record StaffRoleInfoResponse(
        String key,
        String tier,
        boolean privileged,
        List<String> permissions
) {}
