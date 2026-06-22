package com.postwerk.dto;

public record TestModeStatsResponse(
        long total,
        long correct,
        long incorrect,
        long pending,
        int accuracyPercent
) {}
