package com.postwerk.dto.admin;

/**
 * KPI strip for the admin "Quota Overrides" page.
 *
 * @param activeCount                 non-expired overrides.
 * @param creditGrantedThisMonthCents sum of active CREDIT amounts created this calendar month (cents).
 * @param over80Count                 non-expired, non-unlimited overrides where this-month spend exceeds
 *                                    80% of the effective cap.
 * @param expiringIn7Count            active overrides whose expiry falls within the next 7 days.
 * @since 1.0
 */
public record QuotaKpisResponse(
        long activeCount,
        long creditGrantedThisMonthCents,
        long over80Count,
        long expiringIn7Count
) {}
