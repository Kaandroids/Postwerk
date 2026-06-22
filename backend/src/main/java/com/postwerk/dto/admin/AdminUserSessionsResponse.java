package com.postwerk.dto.admin;

/** Active refresh-token (session) count for a user (admin "Users support tooling"). */
public record AdminUserSessionsResponse(
        long activeSessions
) {}
