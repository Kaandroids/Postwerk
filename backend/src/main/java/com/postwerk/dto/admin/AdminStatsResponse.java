package com.postwerk.dto.admin;

/** Response DTO for platform-wide admin dashboard statistics. */
public record AdminStatsResponse(
        long totalUsers,
        long activeUsers,
        long deletedUsers,
        long newUsersLast7Days,
        long newUsersLast30Days,
        long totalPromptTokens,
        long totalOutputTokens,
        long totalAutomationExecutions,
        long successfulExecutions,
        long failedExecutions,
        long activeAutomations,
        long totalEmails
) {}
