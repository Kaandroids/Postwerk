package com.postwerk.dto.admin;

import java.util.List;

/** Response DTO for automation execution statistics (admin view). */
public record AutomationStatsResponse(
        long totalExecutions,
        long successCount,
        long failedCount,
        long runningCount,
        double successRate,
        long activeAutomations,
        long totalAutomations,
        List<TopAutomation> topAutomations
) {
    public record TopAutomation(String automationId, String automationName, long executionCount, long successCount, long failedCount) {}
}
