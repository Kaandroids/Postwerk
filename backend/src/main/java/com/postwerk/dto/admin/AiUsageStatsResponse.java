package com.postwerk.dto.admin;

import java.util.List;

/** Response DTO for platform-wide AI token usage statistics. */
public record AiUsageStatsResponse(
        long totalPromptTokens,
        long totalOutputTokens,
        long totalTokens,
        long totalBillableChars,
        int totalCostCents,
        List<ModelBreakdown> byModel,
        List<OperationBreakdown> byOperation
) {
    public record ModelBreakdown(String model, long promptTokens, long outputTokens, long totalTokens) {}
    public record OperationBreakdown(String operation, long promptTokens, long outputTokens, long totalTokens) {}
}
