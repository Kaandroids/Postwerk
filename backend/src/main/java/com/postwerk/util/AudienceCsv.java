package com.postwerk.util;

import java.util.Arrays;
import java.util.List;

/**
 * Pack/unpack helper for the comma-separated {@code audiencePlans} column shared by announcements and
 * feature flags. Centralizes the (fragile) CSV encoding that was duplicated across both admin services.
 *
 * @since 1.0
 */
public final class AudienceCsv {

    private AudienceCsv() {}

    /** Joins plan names into the stored CSV, or {@code null} when the list is null/empty. */
    public static String pack(List<String> plans) {
        return plans == null || plans.isEmpty() ? null : String.join(",", plans);
    }

    /** Splits the stored CSV back into trimmed, non-empty plan names (never {@code null}). */
    public static List<String> unpack(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
