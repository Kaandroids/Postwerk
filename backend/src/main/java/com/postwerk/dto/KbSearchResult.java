package com.postwerk.dto;

import com.postwerk.model.enums.KbSearchStatus;

import java.util.Map;

/**
 * Outcome of a {@code VECTOR_SEARCH} node's knowledge-base lookup: the retrieve → judge → threshold
 * decision. On {@code MATCHED}, {@code match} carries the chosen entry's field values; otherwise it is
 * empty. {@code confidence}/{@code reason} are the judge's output (present even on {@code NOT_MATCHED}
 * for tracing). {@code candidateCount} is the number of candidates the judge considered.
 *
 * @since 1.0
 */
public record KbSearchResult(
        KbSearchStatus status,
        Map<String, Object> match,
        int confidence,
        String reason,
        int candidateCount
) {}
