package com.postwerk.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Response view of a knowledge-base entry (field values only; never the embedding). */
public record KbEntryResponse(UUID id, Map<String, Object> data, Instant createdAt, Instant updatedAt) {}
