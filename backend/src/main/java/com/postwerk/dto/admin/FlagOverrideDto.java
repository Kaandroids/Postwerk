package com.postwerk.dto.admin;

/** A per-segment override on a feature flag: {@code scope} (plan/org/"Staff") forced {@code value} ("on"/"off"). */
public record FlagOverrideDto(
        String scope,
        String value
) {}
