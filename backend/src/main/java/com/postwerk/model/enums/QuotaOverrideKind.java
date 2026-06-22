package com.postwerk.model.enums;

/**
 * The kind of AI cost-cap exception a {@link com.postwerk.model.QuotaOverride} grants.
 *
 * <ul>
 *   <li>{@code CREDIT} — adds {@code amountCents} of headroom on top of the plan's base monthly cap
 *       (multiple active credits sum).</li>
 *   <li>{@code CAP} — replaces the base cap with {@code amountCents} (the most-permissive active CAP
 *       wins when several apply).</li>
 *   <li>{@code UNLIMITED} — removes the cap entirely; any active UNLIMITED override outranks all
 *       other override kinds.</li>
 * </ul>
 *
 * @since 1.0
 */
public enum QuotaOverrideKind {
    CREDIT,
    CAP,
    UNLIMITED
}
