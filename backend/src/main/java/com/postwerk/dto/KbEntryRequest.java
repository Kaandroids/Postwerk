package com.postwerk.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/** Create/update payload for a single knowledge-base entry: field values keyed by parameter-set field name. */
public record KbEntryRequest(@NotNull Map<String, Object> data) {}
