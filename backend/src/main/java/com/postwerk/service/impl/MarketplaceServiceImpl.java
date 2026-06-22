package com.postwerk.service.impl;

import com.postwerk.service.AutomationConstantsCodec;
import com.postwerk.dto.*;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.*;
import com.postwerk.util.EnumUtil;
import com.postwerk.model.enums.AcquisitionStatus;
import com.postwerk.model.enums.AutomationKind;
import com.postwerk.model.enums.AutomationStatus;
import com.postwerk.model.enums.ListingStatus;
import com.postwerk.model.enums.NodeType;
import com.postwerk.model.enums.ListingVisibility;
import com.postwerk.model.enums.PricingModel;
import com.postwerk.repository.*;
import com.postwerk.service.AutomationService;
import com.postwerk.service.MarketplaceResourceCloner;
import com.postwerk.service.MarketplaceService;
import com.postwerk.service.MarketplaceSnapshotService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Default implementation of {@link MarketplaceService}.
 *
 * @since 1.0
 */
@Service
public class MarketplaceServiceImpl implements MarketplaceService {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceServiceImpl.class);

    private final MarketplaceListingRepository listingRepository;
    private final MarketplaceAcquisitionRepository acquisitionRepository;
    private final MarketplaceReviewRepository reviewRepository;
    private final MarketplacePublishableConstantRepository publishableConstantRepository;
    private final AutomationRepository automationRepository;
    private final AutomationNodeRepository nodeRepository;
    private final ParameterSetRepository parameterSetRepository;
    private final UserRepository userRepository;
    private final AutomationService automationService;
    private final MarketplaceResourceCloner resourceCloner;
    private final MarketplaceSnapshotService snapshotService;
    private final AutomationConstantsCodec constantsCodec;
    private final ObjectMapper objectMapper;

    public MarketplaceServiceImpl(MarketplaceListingRepository listingRepository,
                                  MarketplaceAcquisitionRepository acquisitionRepository,
                                  MarketplaceReviewRepository reviewRepository,
                                  MarketplacePublishableConstantRepository publishableConstantRepository,
                                  AutomationRepository automationRepository,
                                  AutomationNodeRepository nodeRepository,
                                  ParameterSetRepository parameterSetRepository,
                                  UserRepository userRepository,
                                  AutomationService automationService,
                                  MarketplaceResourceCloner resourceCloner,
                                  MarketplaceSnapshotService snapshotService,
                                  AutomationConstantsCodec constantsCodec,
                                  ObjectMapper objectMapper) {
        this.listingRepository = listingRepository;
        this.acquisitionRepository = acquisitionRepository;
        this.reviewRepository = reviewRepository;
        this.publishableConstantRepository = publishableConstantRepository;
        this.automationRepository = automationRepository;
        this.nodeRepository = nodeRepository;
        this.parameterSetRepository = parameterSetRepository;
        this.userRepository = userRepository;
        this.automationService = automationService;
        this.resourceCloner = resourceCloner;
        this.snapshotService = snapshotService;
        this.constantsCodec = constantsCodec;
        this.objectMapper = objectMapper;
    }

    // ─── Publish ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public MarketplaceListingDetailResponse publish(UUID organizationId, UUID userId, PublishListingRequest request) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (author.getPlan() != null && !author.getPlan().isMarketplacePublishEnabled()) {
            throw new IllegalStateException("Your plan does not allow publishing to the marketplace");
        }

        Automation source = automationRepository.findByIdAndOrganizationId(request.automationId(), organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Automation", request.automationId()));

        var sourceNodes = nodeRepository.findByAutomationId(source.getId());

        // Phase 1: an automation that calls integrations cannot itself be published — recursive
        // resource cloning of the referenced integrations is not supported yet.
        if (source.getKind() == AutomationKind.AUTOMATION
                && sourceNodes.stream().anyMatch(n -> n.getNodeType() == NodeType.INTEGRATION_CALL)) {
            throw new IllegalStateException(
                    "Automations that call integrations cannot be published yet. Publish the integration instead.");
        }

        // Enforce the semantic rule catalog: a flow with error-severity issues cannot be published.
        // (Validate is org-scoped — must pass the org, not the user, or the automation isn't found.)
        var validation = automationService.validate(organizationId, source.getId());
        if (!validation.valid()) {
            throw new IllegalStateException(
                    "Cannot publish: please fix these problems first — " + validation.errorSummary());
        }

        ListingVisibility visibility = EnumUtil.parseOrDefault(ListingVisibility.class, request.visibility(), ListingVisibility.PUBLIC);
        PricingModel pricingModel = EnumUtil.parseOrDefault(PricingModel.class, request.pricingModel(), PricingModel.FREE);

        Map<String, JsonNode> constants = constantsCodec.readNodes(source.getConstants());

        // For integrations, snapshot the I/O contract labels from the INPUT/OUTPUT parameter set names
        // when the author did not supply explicit labels, so the contract is visible without exposing internals.
        String ioInLabel = request.ioInLabel();
        String ioOutLabel = request.ioOutLabel();
        if (source.getKind() == AutomationKind.INTEGRATION) {
            if (ioInLabel == null || ioInLabel.isBlank()) {
                ioInLabel = parameterSetNameForNode(sourceNodes, NodeType.INPUT);
            }
            if (ioOutLabel == null || ioOutLabel.isBlank()) {
                ioOutLabel = parameterSetNameForNode(sourceNodes, NodeType.OUTPUT);
            }
        }

        // PRIVATE always ships entries (content-hidden); PUBLIC ships only if the author opts in (D13).
        boolean fullKbEntries = visibility == ListingVisibility.PRIVATE
                || Boolean.TRUE.equals(request.shareKbEntries());
        MarketplaceListing listing = MarketplaceListing.builder()
                .automationId(source.getId())
                .sourceAutomationId(source.getId())
                .kbSharePolicy(fullKbEntries ? "{\"default\":\"FULL\"}" : "{\"default\":\"SCHEMA_ONLY\"}")
                .authorId(userId)
                .organizationId(source.getOrganizationId())
                .name(request.name())
                .tagline(request.tagline())
                .description(request.description())
                .category(request.category())
                .kind(source.getKind())
                .visibility(visibility)
                .pricingModel(pricingModel)
                .price(pricingModel == PricingModel.FREE ? BigDecimal.ZERO
                        : (request.price() != null ? request.price() : BigDecimal.ZERO))
                .version(request.version() != null && !request.version().isBlank() ? request.version() : "1.0.0")
                .icon(request.icon())
                .color(request.color() != null ? request.color() : source.getColor())
                .ioInIcon(request.ioInIcon())
                .ioInLabel(ioInLabel)
                .ioOutIcon(request.ioOutIcon())
                .ioOutLabel(ioOutLabel)
                .nodeCount(sourceNodes.size())
                .constantCount(constants.size())
                .status(ListingStatus.PUBLISHED)
                .build();
        MarketplaceListing saved = listingRepository.save(listing);

        // Freeze the automation + referenced resources into an immutable snapshot (install materializes
        // from it, decoupling installs from the author's evolving/deleted live data).
        snapshotService.capture(saved, source, fullKbEntries);

        if (visibility == ListingVisibility.PRIVATE && request.publishableConstants() != null) {
            int order = 0;
            for (PublishableConstantDto pc : request.publishableConstants()) {
                if (pc.name() == null || !constants.containsKey(pc.name())) continue;
                publishableConstantRepository.save(MarketplacePublishableConstant.builder()
                        .listingId(saved.getId())
                        .name(pc.name())
                        .description(pc.description())
                        .sortOrder(order++)
                        .build());
            }
        }

        return getDetail(userId, saved.getId());
    }

    // ─── Discover ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<MarketplaceListingResponse> discover(UUID userId, String category, String sort, String q) {
        String sortKey = normalizeSort(sort);
        String cat = (category == null || category.isBlank() || "all".equalsIgnoreCase(category)) ? null : category;
        String query = (q == null || q.isBlank()) ? null : q.trim();

        List<MarketplaceListing> listings = listingRepository.discover(cat, query, sortKey);
        Set<UUID> owned = ownedListingIds(userId);
        Map<UUID, AuthorSummaryDto> authorCache = new HashMap<>();
        return listings.stream()
                .map(l -> toListingResponse(l, owned, authorCache))
                .toList();
    }

    // ─── Detail ───────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public MarketplaceListingDetailResponse getDetail(UUID userId, UUID listingId) {
        MarketplaceListing listing = findListing(listingId);
        MarketplaceListingResponse base = toListingResponse(listing, ownedListingIds(userId), new HashMap<>());

        List<NodeChipDto> nodeFlow = List.of();
        List<PublishableConstantDto> publishable = List.of();

        if (listing.getVisibility() == ListingVisibility.PUBLIC) {
            nodeFlow = snapshotService.nodeFlow(listingId).orElseGet(() ->
                    nodeRepository.findByAutomationId(listing.getAutomationId()).stream()
                            .map(n -> new NodeChipDto(n.getNodeType().name(), n.getLabel()))
                            .toList());
        } else {
            Map<String, JsonNode> constants = readSourceConstants(listing);
            publishable = publishableConstantRepository.findByListingIdOrderBySortOrderAsc(listingId).stream()
                    .map(pc -> new PublishableConstantDto(pc.getName(), pc.getDescription(),
                            constantsCodec.rawTypeOf(constants.get(pc.getName()))))
                    .toList();
        }

        return new MarketplaceListingDetailResponse(base, nodeFlow, publishable, getReviews(userId, listingId));
    }

    // ─── Install ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public MarketplaceAcquisitionResponse install(UUID organizationId, UUID userId, UUID listingId) {
        MarketplaceListing listing = findListing(listingId);

        // Scoped to the active org (#4): the same user may install a listing into different orgs.
        acquisitionRepository.findByOrganizationIdAndListingIdAndStatus(organizationId, listingId, AcquisitionStatus.ACTIVE)
                .ifPresent(a -> { throw new IllegalStateException("Listing already installed"); });

        boolean hidden = listing.getVisibility() == ListingVisibility.PRIVATE;

        // Prefer the immutable publish-time snapshot; fall back to the author's live automation for
        // pre-snapshot listings (backward compatible — never breaks an existing listing).
        UUID installedId = snapshotService.materialize(listingId, organizationId, userId, hidden, hidden)
                .orElseGet(() -> {
                    Automation source = automationRepository.findByIdAndUserId(listing.getAutomationId(), listing.getAuthorId())
                            .orElseThrow(() -> new ResourceNotFoundException("Automation", listing.getAutomationId()));
                    var sourceNodes = nodeRepository.findByAutomationId(source.getId());
                    Map<String, String> idMap = resourceCloner.cloneReferencedResources(
                            sourceNodes, listing.getAuthorId(), userId, organizationId);
                    return automationService.installCopy(organizationId, userId, source,
                            cfg -> resourceCloner.rewriteConfig(cfg, idMap), hidden, hidden);
                });

        MarketplaceAcquisition acquisition = acquisitionRepository.save(MarketplaceAcquisition.builder()
                .userId(userId)
                .organizationId(organizationId)
                .listingId(listingId)
                .installedAutomationId(installedId)
                .pricingModel(listing.getPricingModel())
                .price(listing.getPrice())
                .status(AcquisitionStatus.ACTIVE)
                .build());

        listing.setInstallCount(listing.getInstallCount() + 1);
        listingRepository.save(listing);

        return toAcquisitionResponse(acquisition, listing, automationRepository.findById(installedId).orElse(null));
    }

    // ─── Library ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public MarketplaceLibraryResponse getLibrary(UUID organizationId) {
        List<MarketplaceAcquisition> acquisitions =
                acquisitionRepository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(organizationId, AcquisitionStatus.ACTIVE);

        List<MarketplaceAcquisitionResponse> installed = new ArrayList<>();
        List<MarketplaceAcquisitionResponse> purchased = new ArrayList<>();
        for (MarketplaceAcquisition a : acquisitions) {
            MarketplaceListing listing = listingRepository.findById(a.getListingId()).orElse(null);
            Automation installedAutomation = automationRepository.findById(a.getInstalledAutomationId()).orElse(null);
            MarketplaceAcquisitionResponse dto = toAcquisitionResponse(a, listing, installedAutomation);
            installed.add(dto);
            if (a.getPricingModel() != PricingModel.FREE) purchased.add(dto);
        }

        Set<UUID> owned = ownedListingIdsForOrg(organizationId);
        Map<UUID, AuthorSummaryDto> authorCache = new HashMap<>();
        List<MarketplaceListingResponse> published =
                listingRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                        .map(l -> toListingResponse(l, owned, authorCache))
                        .toList();

        return new MarketplaceLibraryResponse(installed, purchased, published);
    }

    // ─── Configure (buyer · private) ──────────────────────────────────────

    @Override
    @Transactional
    public void saveAcquisitionConstants(UUID organizationId, UUID acquisitionId, List<AutomationConstantDto> constants) {
        MarketplaceAcquisition acquisition = findAcquisition(organizationId, acquisitionId);
        Automation automation = loadInstalled(acquisition);

        Set<String> allowed = new HashSet<>();
        publishableConstantRepository.findByListingIdOrderBySortOrderAsc(acquisition.getListingId())
                .forEach(pc -> allowed.add(pc.getName()));

        Map<String, JsonNode> existing = constantsCodec.readNodes(automation.getConstants());
        ObjectNode result = objectMapper.createObjectNode();
        existing.forEach(result::set);

        if (constants != null) {
            for (AutomationConstantDto c : constants) {
                String name = c.name() == null ? "" : c.name().trim();
                if (!allowed.contains(name)) {
                    throw new IllegalArgumentException("Constant not configurable: " + name);
                }
                JsonNode prev = existing.get(name);
                if (prev == null) {
                    throw new IllegalArgumentException("Unknown constant: " + name);
                }
                String type = constantsCodec.rawTypeOf(prev);
                String desc = prev.isObject() ? prev.path("desc").asText("") : "";
                result.set(name, constantsCodec.writeEntry(type, desc, c.value(), prev));
            }
        }

        automation.setConstants(result.toString());
        automationRepository.save(automation);
    }

    @Override
    @Transactional
    public void bindAccounts(UUID organizationId, UUID acquisitionId, List<UUID> accountIds) {
        MarketplaceAcquisition acquisition = findAcquisition(organizationId, acquisitionId);
        Automation automation = loadInstalled(acquisition);
        automation.setAccountIds(accountIds != null ? accountIds.toArray(new UUID[0]) : new UUID[0]);
        automationRepository.save(automation);
    }

    @Override
    @Transactional
    public MarketplaceAcquisitionResponse activate(UUID organizationId, UUID acquisitionId) {
        MarketplaceAcquisition acquisition = findAcquisition(organizationId, acquisitionId);
        Automation automation = loadInstalled(acquisition);
        automation.setStatus(AutomationStatus.ACTIVE);
        automationRepository.save(automation);
        MarketplaceListing listing = listingRepository.findById(acquisition.getListingId()).orElse(null);
        return toAcquisitionResponse(acquisition, listing, automation);
    }

    // ─── Reviews ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ReviewResponse> getReviews(UUID userId, UUID listingId) {
        // Public surface — staff-hidden reviews are excluded.
        List<MarketplaceReview> reviews = reviewRepository.findByListingIdAndHiddenFalseOrderByCreatedAtDesc(listingId);
        Map<UUID, String> names = new HashMap<>();
        return reviews.stream()
                .map(r -> new ReviewResponse(r.getId(), r.getUserId(),
                        names.computeIfAbsent(r.getUserId(), this::userName),
                        r.getRating(), r.getText(), r.getCreatedAt(),
                        r.getUserId().equals(userId)))
                .toList();
    }

    @Override
    @Transactional
    public ReviewResponse addReview(UUID userId, UUID listingId, ReviewRequest request) {
        findListing(listingId);
        MarketplaceReview review = reviewRepository.findByListingIdAndUserId(listingId, userId)
                .orElseGet(() -> MarketplaceReview.builder().listingId(listingId).userId(userId).build());
        review.setRating(request.rating());
        review.setText(request.text());
        MarketplaceReview saved = reviewRepository.save(review);

        recomputeRating(listingId);

        return new ReviewResponse(saved.getId(), userId, userName(userId),
                saved.getRating(), saved.getText(), saved.getCreatedAt(), true);
    }

    // ─── Manage ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void unpublish(UUID userId, UUID listingId) {
        MarketplaceListing listing = findListing(listingId);
        if (!listing.getAuthorId().equals(userId)) {
            throw new ResourceNotFoundException("MarketplaceListing", listingId);
        }
        listing.setStatus(ListingStatus.UNPUBLISHED);
        listingRepository.save(listing);
        snapshotService.delete(listingId);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private void recomputeRating(UUID listingId) {
        Object[] agg = reviewRepository.aggregateRating(listingId);
        long count = ((Number) agg[0]).longValue();
        double avg = ((Number) agg[1]).doubleValue();
        MarketplaceListing listing = listingRepository.findById(listingId).orElse(null);
        if (listing != null) {
            listing.setRatingCount((int) count);
            listing.setRatingAvg(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
            listingRepository.save(listing);
        }
    }

    private MarketplaceListing findListing(UUID listingId) {
        return listingRepository.findByIdAndDeletedAtIsNull(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("MarketplaceListing", listingId));
    }

    private MarketplaceAcquisition findAcquisition(UUID organizationId, UUID acquisitionId) {
        return acquisitionRepository.findByIdAndOrganizationId(acquisitionId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("MarketplaceAcquisition", acquisitionId));
    }

    private Automation loadInstalled(MarketplaceAcquisition acquisition) {
        return automationRepository.findByIdAndOrganizationId(acquisition.getInstalledAutomationId(), acquisition.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Automation", acquisition.getInstalledAutomationId()));
    }

    private Set<UUID> ownedListingIds(UUID userId) {
        if (userId == null) return Set.of();
        Set<UUID> ids = new HashSet<>();
        acquisitionRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, AcquisitionStatus.ACTIVE)
                .forEach(a -> ids.add(a.getListingId()));
        return ids;
    }

    /** Listing ids the active organization has acquired (#4) — for the org library's "owned" flag. */
    private Set<UUID> ownedListingIdsForOrg(UUID organizationId) {
        if (organizationId == null) return Set.of();
        Set<UUID> ids = new HashSet<>();
        acquisitionRepository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(organizationId, AcquisitionStatus.ACTIVE)
                .forEach(a -> ids.add(a.getListingId()));
        return ids;
    }

    private MarketplaceListingResponse toListingResponse(MarketplaceListing l, Set<UUID> owned,
                                                         Map<UUID, AuthorSummaryDto> authorCache) {
        AuthorSummaryDto author = authorCache.computeIfAbsent(l.getAuthorId(), this::authorSummary);
        return new MarketplaceListingResponse(
                l.getId(), l.getName(), l.getTagline(), l.getDescription(), l.getCategory(),
                l.getKind().name(),
                l.getVisibility().name(), l.getPricingModel().name(), l.getPrice(), l.getVersion(),
                l.getIcon(), l.getColor(), l.getIoInIcon(), l.getIoInLabel(), l.getIoOutIcon(), l.getIoOutLabel(),
                l.getNodeCount(), l.getConstantCount(), l.getRatingAvg(), l.getRatingCount(), l.getInstallCount(),
                l.isFeatured(), l.isVerified(), l.getStatus().name(), author, owned.contains(l.getId()),
                l.getCreatedAt(), l.getUpdatedAt());
    }

    private AuthorSummaryDto authorSummary(UUID authorId) {
        List<MarketplaceListing> listings = listingRepository.findByAuthorIdOrderByCreatedAtDesc(authorId);
        List<MarketplaceListing> published = listings.stream()
                .filter(l -> l.getStatus() == ListingStatus.PUBLISHED)
                .toList();
        long installs = published.stream().mapToLong(MarketplaceListing::getInstallCount).sum();
        boolean verified = published.stream().anyMatch(MarketplaceListing::isVerified);
        return new AuthorSummaryDto(authorId, userName(authorId), verified, published.size(), installs);
    }

    private MarketplaceAcquisitionResponse toAcquisitionResponse(MarketplaceAcquisition a, MarketplaceListing listing,
                                                                 Automation installed) {
        MarketplaceListingResponse listingDto = listing != null
                ? toListingResponse(listing, Set.of(a.getListingId()), new HashMap<>())
                : null;
        return new MarketplaceAcquisitionResponse(
                a.getId(), a.getListingId(), a.getInstalledAutomationId(),
                a.getPricingModel().name(), a.getPrice(), a.getStatus().name(),
                installed != null && installed.isHidden(),
                installed != null ? installed.getStatus().name() : null,
                listingDto, a.getCreatedAt());
    }

    private String userName(UUID userId) {
        return userRepository.findById(userId).map(User::getFullName).orElse("Unknown");
    }

    /**
     * Resolves the name of the parameter set referenced by the single node of the given type (INPUT or
     * OUTPUT) in an integration's flow, used to snapshot the I/O contract label at publish time.
     * Returns {@code null} when the node, its {@code parameterSetId}, or the parameter set is absent.
     */
    private String parameterSetNameForNode(List<AutomationNode> nodes, NodeType type) {
        return nodes.stream()
                .filter(n -> n.getNodeType() == type)
                .findFirst()
                .map(n -> {
                    try {
                        JsonNode cfg = objectMapper.readTree(n.getConfig() != null ? n.getConfig() : "{}");
                        String psId = cfg.path("parameterSetId").asText(null);
                        if (psId == null || psId.isBlank()) return null;
                        return parameterSetRepository.findById(UUID.fromString(psId))
                                .map(ParameterSet::getName).orElse(null);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    private Map<String, JsonNode> readSourceConstants(MarketplaceListing listing) {
        return automationRepository.findByIdAndUserId(listing.getAutomationId(), listing.getAuthorId())
                .map(a -> constantsCodec.readNodes(a.getConstants()))
                .orElseGet(Map::of);
    }

    private String normalizeSort(String sort) {
        if (sort == null) return "popular";
        return switch (sort.toLowerCase()) {
            case "new", "installs", "rating", "popular" -> sort.toLowerCase();
            default -> "popular";
        };
    }

}
