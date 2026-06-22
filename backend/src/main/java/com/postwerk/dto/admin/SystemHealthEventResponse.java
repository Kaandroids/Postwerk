package com.postwerk.dto.admin;

import java.time.Instant;

/**
 * A recent platform event for the System Health timeline — derived from infra-relevant audit-log
 * entries (re-sync / pause / resume / probe / cache flush / maintenance toggles).
 *
 * <p>{@code tone}: {@code ok} | {@code warn} | {@code danger}.</p>
 */
public record SystemHealthEventResponse(
        String tone,
        String title,
        String detail,
        Instant at
) {}
