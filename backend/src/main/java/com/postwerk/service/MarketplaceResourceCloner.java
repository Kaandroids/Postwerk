package com.postwerk.service;

import com.postwerk.model.AutomationNode;
import com.postwerk.model.Category;
import com.postwerk.model.ParameterSet;
import com.postwerk.model.Template;
import com.postwerk.repository.CategoryRepository;
import com.postwerk.repository.ParameterSetRepository;
import com.postwerk.repository.TemplateRepository;
import com.postwerk.util.UuidUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Clones an automation's owner-scoped referenced resources (Categories, ParameterSets, Templates)
 * into a buyer's account during a marketplace install, and rewrites node config JSON so the buyer's
 * copy points at the cloned resources.
 *
 * <p>Resource references are discovered generically: every UUID-looking string in a node config is
 * tried against the author's categories, parameter sets and templates. Email-account references
 * ({@code accountIds}, {@code senderAccountId}) and webhook bindings are not cloned — they are
 * cleared so the buyer rebinds their own accounts (see {@link #rewriteConfig}).</p>
 *
 * @since 1.0
 */
@Service
public class MarketplaceResourceCloner {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceResourceCloner.class);

    private final CategoryRepository categoryRepository;
    private final ParameterSetRepository parameterSetRepository;
    private final TemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

    public MarketplaceResourceCloner(CategoryRepository categoryRepository,
                                     ParameterSetRepository parameterSetRepository,
                                     TemplateRepository templateRepository,
                                     ObjectMapper objectMapper) {
        this.categoryRepository = categoryRepository;
        this.parameterSetRepository = parameterSetRepository;
        this.templateRepository = templateRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Clones every resource referenced by the source nodes into the buyer's account and returns an
     * old→new UUID-string map used to rewrite node configs.
     *
     * @param sourceNodes   the author's automation nodes
     * @param sourceOwnerId the author (resources are loaded scoped to this owner)
     * @param buyerId       the buyer (clones are created owned by this user)
     * @param buyerOrgId    the buyer's active organization (#4) — clones are owned by this org so
     *                      they appear in the org the install was made from, and the spend lands there
     */
    public Map<String, String> cloneReferencedResources(List<AutomationNode> sourceNodes,
                                                        UUID sourceOwnerId,
                                                        UUID buyerId,
                                                        UUID buyerOrgId) {
        Set<UUID> candidates = new LinkedHashSet<>();
        for (AutomationNode node : sourceNodes) {
            collectUuids(parse(node.getConfig()), candidates);
        }

        Map<String, String> idMap = new HashMap<>();
        for (UUID id : candidates) {
            cloneCategory(id, sourceOwnerId, buyerId, buyerOrgId, idMap);
            cloneParameterSet(id, sourceOwnerId, buyerId, buyerOrgId, idMap);
            cloneTemplate(id, sourceOwnerId, buyerId, buyerOrgId, idMap);
        }
        return idMap;
    }

    private void cloneCategory(UUID id, UUID owner, UUID buyer, UUID buyerOrgId, Map<String, String> idMap) {
        if (idMap.containsKey(id.toString())) return;
        categoryRepository.findByIdAndUserId(id, owner).ifPresent(src -> {
            Category copy = Category.builder()
                    .userId(buyer)
                    .organizationId(buyerOrgId)
                    .name(src.getName())
                    .color(src.getColor())
                    .description(src.getDescription())
                    .positiveExample(src.getPositiveExample())
                    .negativeExample(src.getNegativeExample())
                    .embedding(src.getEmbedding())
                    .build();
            idMap.put(id.toString(), categoryRepository.save(copy).getId().toString());
        });
    }

    private void cloneParameterSet(UUID id, UUID owner, UUID buyer, UUID buyerOrgId, Map<String, String> idMap) {
        if (idMap.containsKey(id.toString())) return;
        parameterSetRepository.findByIdAndUserId(id, owner).ifPresent(src ->
                idMap.put(id.toString(), copyParameterSet(src, buyer, buyerOrgId).toString()));
    }

    private UUID copyParameterSet(ParameterSet src, UUID buyer, UUID buyerOrgId) {
        ParameterSet copy = ParameterSet.builder()
                .userId(buyer)
                .organizationId(buyerOrgId)
                .name(src.getName())
                .parameters(src.getParameters())
                .build();
        return parameterSetRepository.save(copy).getId();
    }

    private void cloneTemplate(UUID id, UUID owner, UUID buyer, UUID buyerOrgId, Map<String, String> idMap) {
        if (idMap.containsKey(id.toString())) return;
        templateRepository.findByIdAndUserId(id, owner).ifPresent(src -> {
            // A template may reference its own parameter set; clone that too.
            UUID newParamSetId = null;
            if (src.getParameterSetId() != null) {
                String mapped = idMap.get(src.getParameterSetId().toString());
                if (mapped == null) {
                    var ps = parameterSetRepository.findByIdAndUserId(src.getParameterSetId(), owner).orElse(null);
                    if (ps != null) {
                        newParamSetId = copyParameterSet(ps, buyer, buyerOrgId);
                        idMap.put(src.getParameterSetId().toString(), newParamSetId.toString());
                    }
                } else {
                    newParamSetId = UUID.fromString(mapped);
                }
            }
            Template copy = Template.builder()
                    .userId(buyer)
                    .organizationId(buyerOrgId)
                    .name(src.getName())
                    .subject(src.getSubject())
                    .body(src.getBody())
                    .params(src.getParams())
                    .parameterSetId(newParamSetId)
                    .build();
            idMap.put(id.toString(), templateRepository.save(copy).getId().toString());
        });
    }

    /**
     * Rewrites a node's config JSON: remaps every cloned resource id via {@code idMap}, clears the
     * trigger account binding ({@code accountIds}) and sender account ({@code senderAccountId}), and
     * drops the inbound-webhook binding so a buyer-owned endpoint is recreated on save.
     */
    public String rewriteConfig(String json, Map<String, String> idMap) {
        JsonNode root = parse(json);
        if (!root.isObject()) return json == null ? "{}" : json;
        ObjectNode obj = (ObjectNode) root;
        remap(obj, idMap);
        if (obj.has("accountIds")) obj.set("accountIds", objectMapper.createArrayNode());
        obj.remove("senderAccountId");
        obj.remove("webhookEndpointId");
        obj.remove("webhookToken");
        return obj.toString();
    }

    private void remap(JsonNode node, Map<String, String> idMap) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            List<String> fields = new ArrayList<>();
            obj.fieldNames().forEachRemaining(fields::add);
            for (String f : fields) {
                JsonNode child = obj.get(f);
                if (child.isTextual() && idMap.containsKey(child.asText())) {
                    obj.put(f, idMap.get(child.asText()));
                } else {
                    remap(child, idMap);
                }
            }
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                JsonNode el = arr.get(i);
                if (el.isTextual() && idMap.containsKey(el.asText())) {
                    arr.set(i, objectMapper.getNodeFactory().textNode(idMap.get(el.asText())));
                } else {
                    remap(el, idMap);
                }
            }
        }
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
            log.warn("Failed to parse node config during clone: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }
}
