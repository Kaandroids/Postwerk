package com.postwerk.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Request to create or update the requesting user's review of a listing. */
public record ReviewRequest(
        @Min(1) @Max(5) int rating,
        @Size(max = 2000) String text
) {}
