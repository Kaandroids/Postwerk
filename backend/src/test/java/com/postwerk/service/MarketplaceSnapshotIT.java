package com.postwerk.service;

import com.postwerk.BaseIntegrationTest;
import com.postwerk.dto.MarketplaceAcquisitionResponse;
import com.postwerk.dto.MarketplaceListingDetailResponse;
import com.postwerk.dto.PublishListingRequest;
import com.postwerk.model.Automation;
import com.postwerk.model.AutomationEdge;
import com.postwerk.model.AutomationNode;
import com.postwerk.model.Category;
import com.postwerk.model.User;
import com.postwerk.model.enums.AutomationKind;
import com.postwerk.model.enums.AutomationStatus;
import com.postwerk.model.enums.AutomationType;
import com.postwerk.model.enums.NodeType;
import com.postwerk.repository.AutomationEdgeRepository;
import com.postwerk.repository.AutomationNodeRepository;
import com.postwerk.repository.AutomationRepository;
import com.postwerk.repository.CategoryRepository;
import com.postwerk.repository.MarketplaceListingSnapshotRepository;
import com.postwerk.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof of the marketplace snapshot model: an automation + its referenced category are
 * frozen at publish, the author then DELETES the live automation + category, and a buyer can still
 * install — materialized from the snapshot with its own cloned resources. Guards the decoupling that
 * the publish-time snapshot provides.
 */
class MarketplaceSnapshotIT extends BaseIntegrationTest {

    @Autowired private MarketplaceService marketplaceService;
    @Autowired private MarketplaceListingSnapshotRepository snapshotRepository;
    @Autowired private AutomationRepository automationRepository;
    @Autowired private AutomationNodeRepository nodeRepository;
    @Autowired private AutomationEdgeRepository edgeRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private OrganizationRepository organizationRepository;

    @Test
    void publishSnapshots_thenInstallSurvivesAuthorDeletingTheLiveData() {
        // ── Author + buyer (each gets a personal org on registration) ──
        registerAndLogin("kb-snap-author@test.local");
        UUID authorId = userRepository.findByEmail("kb-snap-author@test.local").orElseThrow().getId();
        UUID authorOrgId = organizationRepository.findByOwnerUserIdAndPersonalTrue(authorId).orElseThrow().getId();
        // Null the author's plan so the publish plan-gate (plan != null && !publishEnabled) is satisfied.
        User authorUser = userRepository.findById(authorId).orElseThrow();
        authorUser.setPlan(null);
        userRepository.saveAndFlush(authorUser);

        registerAndLogin("kb-snap-buyer@test.local");
        UUID buyerId = userRepository.findByEmail("kb-snap-buyer@test.local").orElseThrow().getId();
        UUID buyerOrgId = organizationRepository.findByOwnerUserIdAndPersonalTrue(buyerId).orElseThrow().getId();

        // ── Author resources + automation: TRIGGER → LABEL(category) ──
        Category cat = categoryRepository.saveAndFlush(Category.builder()
                .userId(authorId).organizationId(authorOrgId)
                .name("VIP").color("#ffffff").description("VIP leads").build());

        Automation auto = automationRepository.saveAndFlush(Automation.builder()
                .userId(authorId).organizationId(authorOrgId)
                .name("Tagger").type(AutomationType.EMAIL).kind(AutomationKind.AUTOMATION)
                .status(AutomationStatus.PAUSED).accountIds(new UUID[0]).color("#ffffff").build());

        AutomationNode trigger = nodeRepository.saveAndFlush(AutomationNode.builder()
                .automation(auto).nodeType(NodeType.TRIGGER).label("Trigger")
                .positionX(0).positionY(0).config("{\"triggerMode\":\"EMAIL\"}").build());
        AutomationNode label = nodeRepository.saveAndFlush(AutomationNode.builder()
                .automation(auto).nodeType(NodeType.LABEL).label("Tag VIP")
                .positionX(200).positionY(0).config("{\"categoryId\":\"" + cat.getId() + "\"}").build());
        edgeRepository.saveAndFlush(AutomationEdge.builder()
                .automation(auto).sourceNode(trigger).sourceHandle("output")
                .targetNode(label).targetHandle("input").build());

        // ── Publish (PUBLIC) → snapshot captured ──
        var request = new PublishListingRequest(auto.getId(), "Tagger", "Tags VIP leads", null,
                "sales", "PUBLIC", "FREE", null, null, null, null, null, null, null, null, null, null);
        MarketplaceListingDetailResponse detail = marketplaceService.publish(authorOrgId, authorId, request);
        UUID listingId = detail.listing().id();
        assertThat(snapshotRepository.findByListingId(listingId)).isPresent();

        // ── Author DELETES the live automation + referenced category ──
        edgeRepository.deleteAll(edgeRepository.findByAutomationId(auto.getId()));
        nodeRepository.deleteAll(nodeRepository.findByAutomationId(auto.getId()));
        automationRepository.delete(auto);
        categoryRepository.delete(cat);
        automationRepository.flush();
        categoryRepository.flush();

        // ── Buyer installs — must SUCCEED from the snapshot despite the deleted live data ──
        MarketplaceAcquisitionResponse install = marketplaceService.install(buyerOrgId, buyerId, listingId);
        assertThat(install.installedAutomationId()).isNotNull();

        // Buyer received a cloned category (decoupled from the deleted original) ...
        assertThat(categoryRepository.findByOrganizationId(buyerOrgId))
                .extracting(Category::getName).contains("VIP");
        // ... and the materialized flow has both nodes.
        assertThat(nodeRepository.findByAutomationId(install.installedAutomationId()))
                .extracting(n -> n.getNodeType().name())
                .contains("TRIGGER", "LABEL");
    }
}
