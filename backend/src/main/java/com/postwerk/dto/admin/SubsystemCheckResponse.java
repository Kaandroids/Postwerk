package com.postwerk.dto.admin;

import java.time.Instant;

/**
 * A single health-probe result in a subsystem's recent-checks timeline (admin System Health).
 *
 * <p>The platform probes live on demand (no probe-history store yet), so this list currently holds
 * one entry — the most recent probe.</p>
 */
public record SubsystemCheckResponse(
        Instant at,
        boolean ok,
        String message
) {}
