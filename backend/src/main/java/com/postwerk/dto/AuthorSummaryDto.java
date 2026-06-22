package com.postwerk.dto;

import java.util.UUID;

/**
 * Lightweight public summary of a listing author shown on cards and the detail surface.
 *
 * @param id            author user id
 * @param name          author display name (full name)
 * @param verified      whether the author has at least one verified listing
 * @param listingCount  number of published listings by the author
 * @param installCount  total installs across the author's listings
 */
public record AuthorSummaryDto(
        UUID id,
        String name,
        boolean verified,
        int listingCount,
        long installCount
) {}
