package com.postwerk.dto.admin;

/**
 * KPI roll-up for the GDPR / Data Requests console. {@code avgCloseDays} is null when nothing has
 * closed in the last 30 days.
 *
 * @since 1.0
 */
public record GdprKpisResponse(
        long open,
        long overdue,
        long dueSoon,
        long closed30d,
        Integer avgCloseDays,
        long pending,
        long inProgress
) {}
