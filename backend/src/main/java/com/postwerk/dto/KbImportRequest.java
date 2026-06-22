package com.postwerk.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Bulk entry import. Each row is a field-value map. The KB's {@code uniqueField} (if set) drives an
 * upsert keyed on that field; otherwise the import fully replaces the KB's existing entries.
 *
 * @since 1.0
 */
public record KbImportRequest(@NotNull List<Map<String, Object>> rows) {}
