package com.postwerk.dto.admin;

/** Data point for admin timeline charts (date + value). */
public record TimelineDataPoint(
        String date,
        long value
) {}
