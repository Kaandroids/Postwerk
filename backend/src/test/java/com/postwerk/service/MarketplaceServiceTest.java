package com.postwerk.service;

import com.postwerk.TestFixtures;
import com.postwerk.config.EncryptionConfig;
import com.postwerk.dto.AutomationConstantDto;
import com.postwerk.dto.MarketplaceAcquisitionResponse;
import com.postwerk.dto.PublishListingRequest;
import com.postwerk.dto.PublishableConstantDto;
import com.postwerk.dto.ReviewRequest;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.Automation;
import com.postwerk.model.MarketplaceAcquisition;
import com.postwerk.model.MarketplaceListing;
import com.postwerk.model.MarketplacePublishableConstant;
import com.postwerk.model.MarketplaceReview;
import com.postwerk.model.Plan;
import com.postwerk.model.User;
import com.postwerk.model.enums.AcquisitionStatus;
import com.postwerk.model.enums.AutomationStatus;
import com.postwerk.model.enums.ListingStatus;
import com.postwerk.model.enums.ListingVisibility;
import com.postwerk.model.enums.PricingModel;
import com.postwerk.repository.AutomationNodeRepository;
import com.postwerk.repository.AutomationRepository;
import com.postwerk.repository.MarketplaceAcquisitionRepository;
import com.postwerk.repository.MarketplaceListingRepository;
import com.postwerk.repository.MarketplacePublishableConstantRepository;
import com.postwerk.repository.MarketplaceReviewRepository;
import com.postwerk.repository.ParameterSetRepository;
import com.postwerk.repository.UserRepository;
import com.postwerk.service.impl.MarketplaceServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceServiceTest {

    @Mock private MarketplaceListingRepository listingRepository;
    @Mock private MarketplaceAcquisitionRepository acquisitionRepository;
    @Mock private MarketplaceReviewRepository reviewRepository;
    @Mock private MarketplacePublishableConstantRepository publishableConstantRepository;
    @Mock private AutomationRepository automationRepository;
    @Mock private AutomationNodeRepository nodeRepository;
    @Mock private ParameterSetRepository parameterSetRepository;
    @Mock private UserRepository userRepository;
    @Mock private AutomationService automationService;
    @Mock private MarketplaceResourceCloner resourceCloner;
    @Mock private MarketplaceSnapshotService snapshotService;

    private MarketplaceServiceImpl service;

    private UUID userId;
    private UUID orgId;
    private UUID authorId;
    private User author;
    private Automation source;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        EncryptionConfig encryption = new EncryptionConfig(
                java.util.Base64.getEncoder().encodeToString(new byte[32]),
                new org.springframework.mock.env.MockEnvironment());
        AutomationConstantsCodec constantsCodec = new AutomationConstantsCodec(objectMapper, encryption);
        service = new MarketplaceServiceImpl(listingRepository, acquisitionRepository, reviewRepository,
                publishableConstantRepository, automationRepository, nodeRepository, parameterSetRepository,
                userRepository, automationService, resourceCloner, snapshotService, constantsCodec, objectMapper);

        userId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        authorId = UUID.randomUUID();
        author = TestFixtures.createUser();
        author.setId(authorId);
        author.setPlan(planWithPublish(true));

        source = TestFixtures.createAutomation(authorId);
        source.setOrganizationId(orgId);
        source.setConstants("{\"API_KEY\":{\"type\":\"secret\",\"value\":\"enc\",\"desc\":\"The API key\"}}");

        when(userRepository.findById(any())).thenReturn(Optional.of(author));
        when(automationService.validate(any(), any()))
                .thenReturn(new com.postwerk.dto.automation.AutomationValidationResult(true, List.of()));
        when(reviewRepository.findByListingIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
        when(listingRepository.findByAuthorIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
        when(acquisitionRepository.findByUserIdAndStatusOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
    }

    private Plan planWithPublish(boolean enabled) {
        Plan plan = TestFixtures.createPlan();
        plan.setMarketplacePublishEnabled(enabled);
        return plan;
    }

    private MarketplaceListing listing(ListingVisibility visibility) {
        return MarketplaceListing.builder()
                .id(UUID.randomUUID())
                .automationId(source.getId())
                .authorId(authorId)
                .name("Lead Triage")
                .tagline("Sorts leads")
                .category("sales")
                .visibility(visibility)
                .pricingModel(PricingModel.FREE)
                .price(BigDecimal.ZERO)
                .status(ListingStatus.PUBLISHED)
                .installCount(0)
                .build();
    }

    @Test
    void publish_persistsListingAsPublished() {
        when(automationRepository.findByIdAndOrganizationId(source.getId(), orgId)).thenReturn(Optional.of(source));
        when(nodeRepository.countByAutomationId(source.getId())).thenReturn(3);
        when(nodeRepository.findByAutomationId(any())).thenReturn(List.of());
        MarketplaceListing saved = listing(ListingVisibility.PUBLIC);
        when(listingRepository.save(any(MarketplaceListing.class))).thenReturn(saved);
        when(listingRepository.findByIdAndDeletedAtIsNull(saved.getId())).thenReturn(Optional.of(saved));

        var request = new PublishListingRequest(source.getId(), "Lead Triage", "Sorts leads", null,
                "sales", "PUBLIC", "FREE", null, null, null, null, null, null, null, null, null, null);

        var detail = service.publish(orgId, authorId, request);

        assertThat(detail.listing().name()).isEqualTo("Lead Triage");
        assertThat(detail.listing().status()).isEqualTo("PUBLISHED");
        verify(listingRepository).save(any(MarketplaceListing.class));
    }

    @Test
    void publish_planDisabled_throws() {
        author.setPlan(planWithPublish(false));

        var request = new PublishListingRequest(source.getId(), "X", null, null,
                "sales", "PUBLIC", "FREE", null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.publish(orgId, authorId, request))
                .isInstanceOf(IllegalStateException.class);
        verify(listingRepository, never()).save(any());
    }

    @Test
    void publish_privateStoresPublishableConstants() {
        when(automationRepository.findByIdAndOrganizationId(source.getId(), orgId)).thenReturn(Optional.of(source));
        when(nodeRepository.countByAutomationId(source.getId())).thenReturn(1);
        MarketplaceListing saved = listing(ListingVisibility.PRIVATE);
        when(listingRepository.save(any(MarketplaceListing.class))).thenReturn(saved);
        when(listingRepository.findByIdAndDeletedAtIsNull(saved.getId())).thenReturn(Optional.of(saved));
        when(publishableConstantRepository.findByListingIdOrderBySortOrderAsc(saved.getId())).thenReturn(List.of());

        var request = new PublishListingRequest(source.getId(), "Lead Triage", "t", null,
                "sales", "PRIVATE", "FREE", null, null, null, null, null, null, null, null,
                List.of(new PublishableConstantDto("API_KEY", "The key")), null);

        service.publish(orgId, authorId, request);

        verify(publishableConstantRepository).save(any(MarketplacePublishableConstant.class));
    }

    @Test
    void install_clonesResourcesCreatesHiddenCopyAndAcquisition() {
        // The buyer installs while active in a DIFFERENT org than the listing's author org (#4):
        // the copy + acquisition must land in this buyer org, so the spend follows the active org.
        UUID buyerOrgId = UUID.randomUUID();
        MarketplaceListing listing = listing(ListingVisibility.PRIVATE);
        UUID installedId = UUID.randomUUID();
        Automation installed = TestFixtures.createAutomation(userId);
        installed.setId(installedId);
        installed.setHidden(true);
        installed.setStatus(AutomationStatus.PAUSED);

        when(listingRepository.findByIdAndDeletedAtIsNull(listing.getId())).thenReturn(Optional.of(listing));
        when(acquisitionRepository.findByOrganizationIdAndListingIdAndStatus(buyerOrgId, listing.getId(), AcquisitionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(automationRepository.findByIdAndUserId(source.getId(), authorId)).thenReturn(Optional.of(source));
        when(nodeRepository.findByAutomationId(source.getId())).thenReturn(List.of());
        when(resourceCloner.cloneReferencedResources(anyList(), eq(authorId), eq(userId), eq(buyerOrgId)))
                .thenReturn(Map.of("old", "new"));
        when(automationService.installCopy(eq(buyerOrgId), eq(userId), eq(source), any(), eq(true), eq(true)))
                .thenReturn(installedId);
        when(automationRepository.findById(installedId)).thenReturn(Optional.of(installed));
        when(acquisitionRepository.save(any(MarketplaceAcquisition.class))).thenAnswer(inv -> {
            MarketplaceAcquisition a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        MarketplaceAcquisitionResponse response = service.install(buyerOrgId, userId, listing.getId());

        assertThat(response.installedAutomationId()).isEqualTo(installedId);
        assertThat(response.hidden()).isTrue();
        assertThat(listing.getInstallCount()).isEqualTo(1);
        verify(resourceCloner).cloneReferencedResources(anyList(), eq(authorId), eq(userId), eq(buyerOrgId));
        verify(automationService).installCopy(eq(buyerOrgId), eq(userId), eq(source), any(UnaryOperator.class), eq(true), eq(true));
        ArgumentCaptor<MarketplaceAcquisition> acqCaptor = ArgumentCaptor.forClass(MarketplaceAcquisition.class);
        verify(acquisitionRepository).save(acqCaptor.capture());
        assertThat(acqCaptor.getValue().getOrganizationId()).isEqualTo(buyerOrgId);
    }

    @Test
    void install_alreadyInstalled_throws() {
        UUID buyerOrgId = UUID.randomUUID();
        MarketplaceListing listing = listing(ListingVisibility.PUBLIC);
        when(listingRepository.findByIdAndDeletedAtIsNull(listing.getId())).thenReturn(Optional.of(listing));
        when(acquisitionRepository.findByOrganizationIdAndListingIdAndStatus(buyerOrgId, listing.getId(), AcquisitionStatus.ACTIVE))
                .thenReturn(Optional.of(new MarketplaceAcquisition()));

        assertThatThrownBy(() -> service.install(buyerOrgId, userId, listing.getId()))
                .isInstanceOf(IllegalStateException.class);
        verify(automationService, never()).installCopy(any(), any(), any(), any(), eq(true), eq(true));
    }

    @Test
    void saveAcquisitionConstants_rejectsNonPublishableConstant() {
        UUID acqId = UUID.randomUUID();
        MarketplaceListing listing = listing(ListingVisibility.PRIVATE);
        Automation installed = TestFixtures.createAutomation(userId);
        installed.setConstants("{\"API_KEY\":{\"type\":\"text\",\"value\":\"\",\"desc\":\"\"}}");

        MarketplaceAcquisition acq = MarketplaceAcquisition.builder()
                .id(acqId).userId(userId).organizationId(orgId).listingId(listing.getId())
                .installedAutomationId(installed.getId()).build();

        when(acquisitionRepository.findByIdAndOrganizationId(acqId, orgId)).thenReturn(Optional.of(acq));
        when(automationRepository.findByIdAndOrganizationId(installed.getId(), orgId)).thenReturn(Optional.of(installed));
        when(publishableConstantRepository.findByListingIdOrderBySortOrderAsc(listing.getId()))
                .thenReturn(List.of()); // nothing is publishable

        var constants = List.of(new AutomationConstantDto("API_KEY", "secret-value", "text"));

        assertThatThrownBy(() -> service.saveAcquisitionConstants(orgId, acqId, constants))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addReview_recomputesRating() {
        UUID listingId = UUID.randomUUID();
        MarketplaceListing listing = listing(ListingVisibility.PUBLIC);
        listing.setId(listingId);

        when(listingRepository.findByIdAndDeletedAtIsNull(listingId)).thenReturn(Optional.of(listing));
        when(reviewRepository.findByListingIdAndUserId(listingId, userId)).thenReturn(Optional.empty());
        when(reviewRepository.save(any(MarketplaceReview.class))).thenAnswer(inv -> {
            MarketplaceReview r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        when(reviewRepository.aggregateRating(listingId)).thenReturn(new Object[]{1L, 4.0});
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));

        var response = service.addReview(userId, listingId, new ReviewRequest(4, "Great"));

        assertThat(response.rating()).isEqualTo(4);
        assertThat(listing.getRatingCount()).isEqualTo(1);
        assertThat(listing.getRatingAvg()).isEqualByComparingTo(BigDecimal.valueOf(4.0));
    }

    @Test
    void unpublish_setsStatusUnpublished() {
        MarketplaceListing listing = listing(ListingVisibility.PUBLIC);
        when(listingRepository.findByIdAndDeletedAtIsNull(listing.getId())).thenReturn(Optional.of(listing));

        service.unpublish(authorId, listing.getId());

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.UNPUBLISHED);
        verify(listingRepository).save(listing);
    }

    @Test
    void unpublish_notOwner_throws() {
        MarketplaceListing listing = listing(ListingVisibility.PUBLIC);
        when(listingRepository.findByIdAndDeletedAtIsNull(listing.getId())).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> service.unpublish(UUID.randomUUID(), listing.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
