package com.postwerk.dto.admin;

import java.util.List;

/** A listing + its reviews (incl. hidden, for staff) — admin Marketplace Moderation detail modal. */
public record AdminListingDetailResponse(
        AdminListingResponse listing,
        String description,
        List<AdminReviewResponse> reviews
) {}
