package com.postwerk.model.enums;

/**
 * Visibility of a marketplace listing.
 *
 * <p>{@code PUBLIC} listings expose the full node-flow preview and produce a fully editable
 * buyer copy on install. {@code PRIVATE} listings hide their internals; the installed copy is
 * {@code hidden} and only the author-declared publishable constants are buyer-configurable.</p>
 */
public enum ListingVisibility {
    PUBLIC,
    PRIVATE
}
