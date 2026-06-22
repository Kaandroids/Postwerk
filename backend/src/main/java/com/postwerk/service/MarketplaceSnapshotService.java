package com.postwerk.service;

import com.postwerk.dto.NodeChipDto;
import com.postwerk.model.Automation;
import com.postwerk.model.MarketplaceListing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds and consumes the immutable publish-time snapshot of a marketplace listing's automation
 * (doc/KNOWLEDGE_BASE_DESIGN.md Phase 0 + 6b). Publish {@link #capture}s the author's automation +
 * referenced resources into a manifest; install {@link #materialize}s the buyer's copy from it,
 * decoupling installs from the author's live (mutable/deletable) data. All methods are no-ops / empty
 * when no snapshot exists, so callers fall back to the legacy live-read path (backward compatible).
 *
 * @since 1.0
 */
public interface MarketplaceSnapshotService {

    /**
     * Freezes {@code source}'s automation + nodes/edges + referenced resources into a manifest stored
     * against the listing. {@code fullKbEntries} = include knowledge-base entries (FULL share, used for
     * PRIVATE listings) vs schema only.
     */
    void capture(MarketplaceListing listing, Automation source, boolean fullKbEntries);

    /**
     * Materializes a buyer-owned copy from the listing's snapshot. Returns the new automation id, or
     * {@link Optional#empty()} when the listing has no snapshot (caller uses the live-read fallback).
     */
    Optional<UUID> materialize(UUID listingId, UUID buyerOrgId, UUID buyerId, boolean hidden, boolean locked);

    /** Node-flow chips from the snapshot manifest for the PUBLIC detail view, or empty when none. */
    Optional<List<NodeChipDto>> nodeFlow(UUID listingId);

    /** Removes the listing's snapshot (on unpublish). */
    void delete(UUID listingId);
}
