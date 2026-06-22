package com.postwerk.dto;

import java.util.List;

/** Result of a bulk import operation with success/failure counts. */
public record ImportResultDto(
        int imported,
        int failed,
        List<String> errors
) {}
