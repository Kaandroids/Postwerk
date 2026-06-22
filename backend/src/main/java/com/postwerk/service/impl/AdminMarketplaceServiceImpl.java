package com.postwerk.service.impl;

import com.postwerk.dto.admin.AdminListingDetailResponse;
import com.postwerk.dto.admin.AdminListingResponse;
import com.postwerk.dto.admin.AdminReviewResponse;
import com.postwerk.dto.admin.MarketplaceKpisResponse;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.AuditAction;
import com.postwerk.model.MarketplaceListing;
import com.postwerk.model.MarketplaceReview;
import com.postwerk.model.User;
import com.postwerk.model.enums.ListingStatus;
import com.postwerk.model.enums.ListingVisibility;
import com.postwerk.model.enums.PricingModel;
import com.postwerk.repository.MarketplaceListingRepository;
import com.postwerk.repository.MarketplaceReviewRepository;
import com.postwerk.repository.UserRepository;
import com.postwerk.service.AdminMarketplaceService;
import com.postwerk.service.AuditService;
import com.postwerk.util.InMemoryPage;
import com.postwerk.util.SafeStrings;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default {@link AdminMarketplaceService}. Listings + reviews are loaded cross-tenant and
 * filtered/sorted/paginated in-memory (consistent with the other admin tooling); author + listing
 * names are batch-resolved (N+1-safe). Hiding/deleting a review recomputes the listing's rating.
 *
 * @since 1.0
 */
@Service
public class AdminMarketplaceServiceImpl implements AdminMarketplaceService {

    private final MarketplaceListingRepository listingRepository;
    private final MarketplaceReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public AdminMarketplaceServiceImpl(MarketplaceListingRepository listingRepository,
                                       MarketplaceReviewRepository reviewRepository,
                                       UserRepository userRepository,
                                       AuditService auditService) {
        this.listingRepository = listingRepository;
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    private record Author(String name, String email) {}

    // ── Listings list ──────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public Page<AdminListingResponse> listListings(String search, String status, String kind, String pricing,
                                                   String sort, Pageable pageable) {
        List<MarketplaceListing> all = listingRepository.findAllByOrderByCreatedAtDesc();
        Map<UUID, Author> authors = resolveAuthors(all.stream().map(MarketplaceListing::getAuthorId));
        String q = search == null ? "" : search.trim().toLowerCase();

        List<AdminListingResponse> rows = all.stream()
                .map(l -> toListing(l, authors))
                .filter(r -> {
                    if (!q.isEmpty() && !(SafeStrings.containsIgnoreCase(r.name(), q)
                            || SafeStrings.containsIgnoreCase(r.authorName(), q)
                            || SafeStrings.containsIgnoreCase(r.authorEmail(), q)
                            || SafeStrings.containsIgnoreCase(r.slug(), q))) return false;
                    if (status != null && !status.isBlank() && !status.equals(r.status())) return false;
                    if (kind != null && !kind.isBlank() && !kind.equalsIgnoreCase(r.kind())) return false;
                    if (pricing != null && !pricing.isBlank()) {
                        boolean free = "FREE".equalsIgnoreCase(r.pricingModel());
                        if ("FREE".equalsIgnoreCase(pricing) && !free) return false;
                        if ("PAID".equalsIgnoreCase(pricing) && free) return false;
                    }
                    return true;
                })
                .sorted(listingComparator(sort))
                .collect(Collectors.toList());

        return InMemoryPage.of(rows, pageable);
    }

    private Comparator<AdminListingResponse> listingComparator(String sort) {
        return switch (sort == null ? "newest" : sort) {
            case "installs" -> Comparator.comparingLong(AdminListingResponse::installCount).reversed();
            case "rating" -> Comparator.comparing(AdminListingResponse::ratingAvg, Comparator.reverseOrder());
            default -> Comparator.comparing(AdminListingResponse::createdAt, Comparator.reverseOrder());
        };
    }

    // ── Reviews list ────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public Page<AdminReviewResponse> listReviews(String search, Integer rating, String status, String sort, Pageable pageable) {
        List<MarketplaceReview> all = reviewRepository.findAllByOrderByCreatedAtDesc();
        Map<UUID, Author> authors = resolveAuthors(all.stream().map(MarketplaceReview::getUserId));
        Map<UUID, String> listingNames = new HashMap<>();
        Set<UUID> listingIds = all.stream().map(MarketplaceReview::getListingId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!listingIds.isEmpty()) {
            listingRepository.findAllById(listingIds).forEach(l -> listingNames.put(l.getId(), l.getName()));
        }
        String q = search == null ? "" : search.trim().toLowerCase();

        List<AdminReviewResponse> rows = all.stream()
                .map(r -> toReview(r, authors, listingNames.get(r.getListingId())))
                .filter(r -> {
                    if (!q.isEmpty() && !(SafeStrings.containsIgnoreCase(r.text(), q)
                            || SafeStrings.containsIgnoreCase(r.authorName(), q)
                            || SafeStrings.containsIgnoreCase(r.authorEmail(), q)
                            || SafeStrings.containsIgnoreCase(r.listingName(), q))) return false;
                    if (rating != null && r.rating() != rating) return false;
                    if (status != null && !status.isBlank()) {
                        if ("VISIBLE".equalsIgnoreCase(status) && r.hidden()) return false;
                        if ("HIDDEN".equalsIgnoreCase(status) && !r.hidden()) return false;
                    }
                    return true;
                })
                .sorted("lowest".equals(sort)
                        ? Comparator.comparingInt(AdminReviewResponse::rating)
                        : Comparator.comparing(AdminReviewResponse::createdAt, Comparator.reverseOrder()))
                .collect(Collectors.toList());

        return InMemoryPage.of(rows, pageable);
    }

    // ── KPIs ──────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public MarketplaceKpisResponse kpis() {
        List<MarketplaceListing> listings = listingRepository.findAllByOrderByCreatedAtDesc();
        long total = listings.size();
        long takenDown = listings.stream().filter(MarketplaceListing::isTakenDown).count();
        long paused = listings.stream().filter(l -> !l.isTakenDown() && l.getStatus() == ListingStatus.UNPUBLISHED).count();
        long published = listings.stream().filter(l -> !l.isTakenDown() && l.getStatus() == ListingStatus.PUBLISHED
                && l.getVisibility() == ListingVisibility.PUBLIC).count();
        long installs = listings.stream().mapToLong(MarketplaceListing::getInstallCount).sum();
        double avgRating = round2(listings.stream().filter(l -> l.getRatingCount() > 0)
                .mapToDouble(l -> l.getRatingAvg().doubleValue()).average().orElse(0));

        List<MarketplaceReview> reviews = reviewRepository.findAllByOrderByCreatedAtDesc();
        long totalReviews = reviews.size();
        long hidden = reviews.stream().filter(MarketplaceReview::isHidden).count();
        long visible = totalReviews - hidden;
        double reviewAvg = round2(reviews.stream().filter(r -> !r.isHidden())
                .mapToInt(MarketplaceReview::getRating).average().orElse(0));
        long lowRatings = reviews.stream().filter(r -> !r.isHidden() && r.getRating() <= 2).count();

        return new MarketplaceKpisResponse(total, published, paused, takenDown, installs, avgRating, 0,
                totalReviews, visible, hidden, reviewAvg, lowRatings, 0);
    }

    // ── Detail ────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public AdminListingDetailResponse getListing(UUID listingId) {
        MarketplaceListing l = requireListing(listingId);
        Map<UUID, Author> la = resolveAuthors(java.util.stream.Stream.of(l.getAuthorId()));
        // Staff see ALL reviews (including hidden) for the listing.
        List<MarketplaceReview> reviews = reviewRepository.findByListingIdOrderByCreatedAtDesc(listingId);
        Map<UUID, Author> ra = resolveAuthors(reviews.stream().map(MarketplaceReview::getUserId));
        List<AdminReviewResponse> reviewRows = reviews.stream()
                .map(r -> toReview(r, ra, l.getName())).toList();
        return new AdminListingDetailResponse(toListing(l, la), l.getDescription(), reviewRows);
    }

    // ── Listing mutations ───────────────────────────────────────────────────────
    @Override
    @Transactional
    public AdminListingResponse takeDown(UUID listingId, String reason, UUID actorUserId, String ip) {
        MarketplaceListing l = requireListing(listingId);
        l.setTakenDown(true);
        listingRepository.save(l);
        auditService.log(actorUserId, AuditAction.LISTING_TAKEN_DOWN,
                "Took down listing " + l.getName() + (reason != null && !reason.isBlank() ? " · " + reason.trim() : ""), ip);
        return toListing(l, resolveAuthors(java.util.stream.Stream.of(l.getAuthorId())));
    }

    @Override
    @Transactional
    public AdminListingResponse restore(UUID listingId, UUID actorUserId, String ip) {
        MarketplaceListing l = requireListing(listingId);
        l.setTakenDown(false);
        listingRepository.save(l);
        auditService.log(actorUserId, AuditAction.LISTING_RESTORED, "Restored listing " + l.getName(), ip);
        return toListing(l, resolveAuthors(java.util.stream.Stream.of(l.getAuthorId())));
    }

    @Override
    @Transactional
    public AdminListingResponse setFeatured(UUID listingId, boolean featured, UUID actorUserId, String ip) {
        MarketplaceListing l = requireListing(listingId);
        l.setFeatured(featured);
        listingRepository.save(l);
        auditService.log(actorUserId, featured ? AuditAction.LISTING_FEATURED : AuditAction.LISTING_UNFEATURED,
                (featured ? "Featured listing " : "Unfeatured listing ") + l.getName(), ip);
        return toListing(l, resolveAuthors(java.util.stream.Stream.of(l.getAuthorId())));
    }

    // ── Review mutations ────────────────────────────────────────────────────────
    @Override
    @Transactional
    public AdminReviewResponse setReviewHidden(UUID reviewId, boolean hidden, UUID actorUserId, String ip) {
        MarketplaceReview r = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("MarketplaceReview", reviewId.toString()));
        r.setHidden(hidden);
        reviewRepository.save(r);
        recomputeRating(r.getListingId());
        auditService.log(actorUserId, hidden ? AuditAction.REVIEW_HIDDEN : AuditAction.REVIEW_UNHIDDEN,
                (hidden ? "Hid review on listing " : "Unhid review on listing ") + r.getListingId(), ip);
        Map<UUID, Author> a = resolveAuthors(java.util.stream.Stream.of(r.getUserId()));
        String listingName = listingRepository.findById(r.getListingId()).map(MarketplaceListing::getName).orElse(null);
        return toReview(r, a, listingName);
    }

    @Override
    @Transactional
    public void deleteReview(UUID reviewId, String reason, UUID actorUserId, String ip) {
        MarketplaceReview r = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("MarketplaceReview", reviewId.toString()));
        UUID listingId = r.getListingId();
        reviewRepository.delete(r);
        recomputeRating(listingId);
        auditService.log(actorUserId, AuditAction.REVIEW_DELETED,
                "Deleted review on listing " + listingId + (reason != null && !reason.isBlank() ? " · " + reason.trim() : ""), ip);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────
    private void recomputeRating(UUID listingId) {
        Object[] agg = reviewRepository.aggregateRating(listingId);
        // AVG over zero rows is NULL (e.g. after the listing's last review is deleted/hidden) — guard it.
        long count = agg == null || agg[0] == null ? 0L : ((Number) agg[0]).longValue();
        double avg = agg == null || agg[1] == null ? 0.0 : ((Number) agg[1]).doubleValue();
        listingRepository.findById(listingId).ifPresent(l -> {
            l.setRatingCount((int) count);
            l.setRatingAvg(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
            listingRepository.save(l);
        });
    }

    private MarketplaceListing requireListing(UUID id) {
        return listingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MarketplaceListing", id.toString()));
    }

    private Map<UUID, Author> resolveAuthors(java.util.stream.Stream<UUID> ids) {
        Set<UUID> set = ids.filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Author> map = new HashMap<>();
        if (!set.isEmpty()) {
            for (User u : userRepository.findAllById(set)) {
                map.put(u.getId(), new Author(u.getFullName(), u.getEmail()));
            }
        }
        return map;
    }

    private AdminListingResponse toListing(MarketplaceListing l, Map<UUID, Author> authors) {
        Author a = authors.get(l.getAuthorId());
        return new AdminListingResponse(
                l.getId(), l.getName(), slug(l.getName()),
                a != null ? a.name() : null, a != null ? a.email() : null,
                l.getKind() != null ? l.getKind().name() : null,
                l.getPricingModel() != null ? l.getPricingModel().name() : PricingModel.FREE.name(),
                l.getPrice(), deriveStatus(l), l.isFeatured(), l.isTakenDown(),
                l.getInstallCount(), l.getRatingAvg(), l.getRatingCount(), l.getCategory(), l.getCreatedAt());
    }

    private AdminReviewResponse toReview(MarketplaceReview r, Map<UUID, Author> authors, String listingName) {
        Author a = authors.get(r.getUserId());
        return new AdminReviewResponse(r.getId(), r.getListingId(), listingName,
                a != null ? a.name() : null, a != null ? a.email() : null,
                r.getRating(), r.getText(), r.isHidden(), r.getCreatedAt());
    }

    /** Staff-facing moderation status: TAKEN_DOWN > PAUSED (unpublished) > PRIVATE > PUBLIC. */
    private static String deriveStatus(MarketplaceListing l) {
        if (l.isTakenDown()) return "TAKEN_DOWN";
        if (l.getStatus() == ListingStatus.UNPUBLISHED) return "PAUSED";
        if (l.getVisibility() == ListingVisibility.PRIVATE) return "PRIVATE";
        return "PUBLIC";
    }

    private static String slug(String name) {
        if (name == null) return "";
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
