package com.postwerk.util;

/**
 * Shared monetary conversion constants for the cost-based AI quota system.
 *
 * <p>AI usage is tracked in <em>cost micros</em> (1&nbsp;USD = 1,000,000 micros). Plan limits and
 * admin reporting are expressed in cents, so {@code 1 cent = 10,000 micros}.</p>
 *
 * @since 1.0
 */
public final class MonetaryConstants {

    /** Number of cost micros in one cent (1,000,000 micros / 100 cents). */
    public static final long MICROS_PER_CENT = 10_000L;

    private MonetaryConstants() {
    }
}
