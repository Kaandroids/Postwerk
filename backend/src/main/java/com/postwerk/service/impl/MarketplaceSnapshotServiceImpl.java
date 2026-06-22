package com.postwerk.service.impl;

import com.postwerk.dto.EdgeSpec;
import com.postwerk.dto.MarketplaceSnapshotManifest;
import com.postwerk.dto.MarketplaceSnapshotManifest.AutomationSpec;
import com.postwerk.dto.MarketplaceSnapshotManifest.KbEntrySpec;
import com.postwerk.dto.MarketplaceSnapshotManifest.ResourceSpec;
import com.postwerk.dto.NodeChipDto;
import com.postwerk.dto.NodeSpec;
import com.postwerk.model.Automation;
import com.postwerk.model.AutomationEdge;
import com.postwerk.model.AutomationNode;
import com.postwerk.model.Category;
import com.postwerk.model.KnowledgeBase;
import com.postwerk.model.KnowledgeBaseEntry;
import com.postwerk.model.MarketplaceListing;
import com.postwerk.model.MarketplaceListingSnapshot;
import com.postwerk.model.ParameterSet;
import com.postwerk.model.Template;
import com.postwerk.model.enums.AutomationKind;
import com.postwerk.repository.AutomationEdgeRepository;
import com.postwerk.repository.AutomationNodeRepository;
import com.postwerk.repository.CategoryRepository;
import com.postwerk.repository.KnowledgeBaseEntryRepository;
import com.postwerk.repository.KnowledgeBaseRepository;
import com.postwerk.repository.MarketplaceListingSnapshotRepository;
import com.postwerk.repository.ParameterSetRepository;
import com.postwerk.repository.TemplateRepository;
import com.postwerk.service.AutomationService;
import com.postwerk.service.MarketplaceResourceCloner;
import com.postwerk.service.MarketplaceSnapshotService;
import com.postwerk.util.UuidUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Default implementation of {@link MarketplaceSnapshotService}.
 *
 * <p>Publish freezes the author's automation + nodes/edges + referenced resources (categories,
 * parameter sets, templates, knowledge bases) into a JSON manifest. Install materializes a buyer-owned
 * copy from the manifest — recreating each resource, building an old→new id map, rewriting node configs
 * via {@link MarketplaceResourceCloner#rewriteConfig}, and creating the flow via
 * {@link AutomationService#createSnapshotCopy} — so installs never depend on the author's live data.</p>
 *
 * @since 1.0
 */
@Service
public class MarketplaceSnapshotServiceImpl implements MarketplaceSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceSnapshotServiceImpl.class);

    private final MarketplaceListingSnapshotRepository snapshotRepository;
    private final AutomationNodeRepository nodeRepository;
    private final AutomationEdgeRepository edgeRepository;
    private final CategoryRepository categoryRepository;
    private final ParameterSetRepository parameterSetRepository;
    private final TemplateRepository templateRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseEntryRepository knowledgeBaseEntryRepository;
    private final AutomationService automationService;
    private final MarketplaceResourceCloner resourceCloner;
    private final ObjectMapper objectMapper;

    public MarketplaceSnapshotServiceImpl(MarketplaceListingSnapshotRepository snapshotRepository,
                                          AutomationNodeRepository nodeRepository,
                                          AutomationEdgeRepository edgeRepository,
                                          CategoryRepository categoryRepository,
                                          ParameterSetRepository parameterSetRepository,
                                          TemplateRepository templateRepository,
                                          KnowledgeBaseRepository knowledgeBaseRepository,
                                          KnowledgeBaseEntryRepository knowledgeBaseEntryRepository,
                                          AutomationService automationService,
                                          MarketplaceResourceCloner resourceCloner,
                                          ObjectMapper objectMapper) {
        this.snapshotRepository = snapshotRepository;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.categoryRepository = categoryRepository;
        this.parameterSetRepository = parameterSetRepository;
        this.templateRepository = templateRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.knowledgeBaseEntryRepository = knowledgeBaseEntryRepository;
        this.automationService = automationService;
        this.resourceCloner = resourceCloner;
        this.objectMapper = objectMapper;
    }

    // ── Capture ────────────────────────────────────────────────

    @Override
    @Transactional
    public void capture(MarketplaceListing listing, Automation source, boolean fullKbEntries) {
        UUID ownerId = source.getUserId();
        List<AutomationNode> nodes = nodeRepository.findByAutomationId(source.getId());
        List<AutomationEdge> edges = edgeRepository.findByAutomationId(source.getId());

        Map<UUID, Integer> indexById = new HashMap<>();
        List<NodeSpec> nodeSpecs = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            AutomationNode n = nodes.get(i);
            indexById.put(n.getId(), i);
            nodeSpecs.add(new NodeSpec(n.getNodeType().name(), n.getLabel(),
                    n.getPositionX(), n.getPositionY(), n.getConfig()));
        }
        List<EdgeSpec> edgeSpecs = new ArrayList<>();
        for (AutomationEdge e : edges) {
            Integer s = indexById.get(e.getSourceNode().getId());
            Integer t = indexById.get(e.getTargetNode().getId());
            if (s == null || t == null) continue;
            edgeSpecs.add(new EdgeSpec(s, e.getSourceHandle(), t, e.getTargetHandle()));
        }

        List<ResourceSpec> resources = captureResources(nodes, ownerId, fullKbEntries);

        AutomationSpec automationSpec = new AutomationSpec(
                source.getName(), source.getDescription(), source.getKind().name(),
                source.getColor(), source.getConstants(), source.getFlowData());

        String manifestJson;
        try {
            manifestJson = objectMapper.writeValueAsString(
                    new MarketplaceSnapshotManifest(automationSpec, nodeSpecs, edgeSpecs, resources));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize marketplace snapshot manifest", ex);
        }

        snapshotRepository.deleteByListingId(listing.getId()); // re-publish replaces the snapshot
        snapshotRepository.save(MarketplaceListingSnapshot.builder()
                .listingId(listing.getId())
                .version(listing.getVersion() != null ? listing.getVersion() : "1.0.0")
                .manifest(manifestJson)
                .build());
    }

    /** Walks node configs for referenced resource ids and captures each resource's content. */
    private List<ResourceSpec> captureResources(List<AutomationNode> nodes, UUID ownerId, boolean fullKbEntries) {
        Set<UUID> candidates = new HashSet<>();
        for (AutomationNode n : nodes) {
            collectUuids(parse(n.getConfig()), candidates);
        }

        List<ResourceSpec> resources = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        Deque<UUID> pending = new ArrayDeque<>(candidates);
        while (!pending.isEmpty()) {
            UUID id = pending.poll();
            if (!seen.add(id)) continue;

            Category cat = categoryRepository.findByIdAndUserId(id, ownerId).orElse(null);
            if (cat != null) {
                resources.add(new ResourceSpec("CATEGORY", id.toString(), cat.getName(), cat.getColor(),
                        cat.getDescription(), cat.getPositiveExample(), cat.getNegativeExample(), cat.getEmbedding(),
                        null, null, null, null, null, null, null, null));
                continue;
            }
            ParameterSet ps = parameterSetRepository.findByIdAndUserId(id, ownerId).orElse(null);
            if (ps != null) {
                resources.add(new ResourceSpec("PARAMETER_SET", id.toString(), ps.getName(), null,
                        null, null, null, null, ps.getParameters(), null, null, null, null, null, null, null));
                continue;
            }
            Template tpl = templateRepository.findByIdAndUserId(id, ownerId).orElse(null);
            if (tpl != null) {
                resources.add(new ResourceSpec("TEMPLATE", id.toString(), tpl.getName(), null,
                        null, null, null, null, null, tpl.getSubject(), tpl.getBody(), tpl.getParams(),
                        str(tpl.getParameterSetId()), null, null, null));
                if (tpl.getParameterSetId() != null) pending.add(tpl.getParameterSetId());
                continue;
            }
            KnowledgeBase kb = knowledgeBaseRepository.findByIdAndUserId(id, ownerId).orElse(null);
            if (kb != null) {
                List<KbEntrySpec> entries = List.of();
                if (fullKbEntries) {
                    entries = knowledgeBaseEntryRepository.findByKnowledgeBaseId(kb.getId()).stream()
                            .map(e -> new KbEntrySpec(e.getData(), e.getEmbedding(), e.getSearchText(), e.getContentHash()))
                            .toList();
                }
                resources.add(new ResourceSpec("KNOWLEDGE_BASE", id.toString(), kb.getName(), null,
                        kb.getDescription(), null, null, null, null, null, null, null,
                        str(kb.getParameterSetId()), kb.getFieldRoles(), kb.getUniqueField(), entries));
                if (kb.getParameterSetId() != null) pending.add(kb.getParameterSetId());
            }
            // Any other UUID (e.g. an account id) is not a cloneable resource — ignored.
        }
        return resources;
    }

    // ── Materialize ────────────────────────────────────────────

    @Override
    @Transactional
    public Optional<UUID> materialize(UUID listingId, UUID buyerOrgId, UUID buyerId, boolean hidden, boolean locked) {
        MarketplaceListingSnapshot snapshot = snapshotRepository.findByListingId(listingId).orElse(null);
        if (snapshot == null) {
            return Optional.empty(); // caller falls back to the live-read path
        }
        MarketplaceSnapshotManifest manifest;
        try {
            manifest = objectMapper.readValue(snapshot.getManifest(), MarketplaceSnapshotManifest.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read marketplace snapshot manifest", ex);
        }

        Map<String, String> idMap = new HashMap<>();
        List<ResourceSpec> resources = manifest.resources() != null ? manifest.resources() : List.of();

        // Pass 1: leaf resources (no cross-references) — paramsets + categories.
        for (ResourceSpec r : resources) {
            if ("PARAMETER_SET".equals(r.type())) {
                ParameterSet ps = parameterSetRepository.save(ParameterSet.builder()
                        .userId(buyerId).organizationId(buyerOrgId)
                        .name(r.name()).parameters(r.parameters() != null ? r.parameters() : "[]").build());
                idMap.put(r.originalId(), ps.getId().toString());
            } else if ("CATEGORY".equals(r.type())) {
                Category cat = categoryRepository.save(Category.builder()
                        .userId(buyerId).organizationId(buyerOrgId)
                        .name(r.name()).color(r.color()).description(r.description())
                        .positiveExample(r.positiveExample()).negativeExample(r.negativeExample())
                        .embedding(r.embedding()).build());
                idMap.put(r.originalId(), cat.getId().toString());
            }
        }
        // Pass 2: resources that reference a paramset — templates + knowledge bases.
        for (ResourceSpec r : resources) {
            if ("TEMPLATE".equals(r.type())) {
                Template tpl = templateRepository.save(Template.builder()
                        .userId(buyerId).organizationId(buyerOrgId)
                        .name(r.name()).subject(r.subject()).body(r.body()).params(r.params())
                        .parameterSetId(remapUuid(r.parameterSetId(), idMap)).build());
                idMap.put(r.originalId(), tpl.getId().toString());
            } else if ("KNOWLEDGE_BASE".equals(r.type())) {
                KnowledgeBase kb = knowledgeBaseRepository.save(KnowledgeBase.builder()
                        .organizationId(buyerOrgId).userId(buyerId)
                        .name(r.name()).description(r.description())
                        .parameterSetId(remapUuid(r.parameterSetId(), idMap))
                        .fieldRoles(r.fieldRoles() != null ? r.fieldRoles() : "{}")
                        .uniqueField(r.uniqueField())
                        .hidden(hidden).locked(locked).build());
                idMap.put(r.originalId(), kb.getId().toString());
                if (r.entries() != null) {
                    for (KbEntrySpec e : r.entries()) {
                        knowledgeBaseEntryRepository.save(KnowledgeBaseEntry.builder()
                                .knowledgeBaseId(kb.getId()).organizationId(buyerOrgId)
                                .data(e.data()).embedding(e.embedding()).searchText(e.searchText())
                                .contentHash(e.contentHash()).embeddingDirty(false).build());
                    }
                }
            }
        }

        List<NodeSpec> nodeSpecs = new ArrayList<>();
        for (NodeSpec n : manifest.nodes()) {
            String cfg = resourceCloner.rewriteConfig(n.config() != null ? n.config() : "{}", idMap);
            nodeSpecs.add(new NodeSpec(n.nodeType(), n.label(), n.positionX(), n.positionY(), cfg));
        }

        AutomationSpec a = manifest.automation();
        UUID installedId = automationService.createSnapshotCopy(buyerOrgId, buyerId,
                a.name(), a.description(), AutomationKind.valueOf(a.kind()), a.color(), a.constants(), a.flowData(),
                nodeSpecs, manifest.edges(), hidden, locked);
        return Optional.of(installedId);
    }

    // ── Detail node-flow ───────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<List<NodeChipDto>> nodeFlow(UUID listingId) {
        return snapshotRepository.findByListingId(listingId).map(snapshot -> {
            try {
                MarketplaceSnapshotManifest manifest =
                        objectMapper.readValue(snapshot.getManifest(), MarketplaceSnapshotManifest.class);
                return manifest.nodes().stream()
                        .map(n -> new NodeChipDto(n.nodeType(), n.label()))
                        .toList();
            } catch (Exception ex) {
                log.warn("Failed to read snapshot manifest for listing {}: {}", listingId, ex.getMessage());
                return List.<NodeChipDto>of();
            }
        });
    }

    @Override
    @Transactional
    public void delete(UUID listingId) {
        snapshotRepository.deleteByListingId(listingId);
    }

    // ── helpers ────────────────────────────────────────────────

    private UUID remapUuid(String original, Map<String, String> idMap) {
        if (original == null) return null;
        String mapped = idMap.get(original);
        return UuidUtil.parseOrNull(mapped != null ? mapped : original);
    }

    private static String str(UUID id) {
        return id != null ? id.toString() : null;
    }

    private void collectUuids(JsonNode node, Set<UUID> out) {
        if (node == null) return;
        if (node.isTextual()) {
            UUID parsed = UuidUtil.parseOrNull(node.asText());
            if (parsed != null) out.add(parsed);
        } else if (node.isContainerNode()) {
            node.forEach(child -> collectUuids(child, out));
        }
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }
}
