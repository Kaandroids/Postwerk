package com.postwerk.service.executor;

import com.postwerk.dto.AiAttachment;
import com.postwerk.dto.CategoryCandidate;
import com.postwerk.dto.ClassificationResult;
import com.postwerk.model.AuditAction;
import com.postwerk.model.Category;
import com.postwerk.model.Email;
import com.postwerk.repository.CategoryRepository;
import com.postwerk.repository.EmailRepository;
import com.postwerk.service.AuditService;
import com.postwerk.service.EmbeddingService;
import com.postwerk.service.GeminiService;
import com.postwerk.util.EmailTextBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.postwerk.util.NodeConfigReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Executes the CATEGORIZE node in an automation workflow.
 *
 * <p>Classification pipeline:
 * <ol>
 *   <li>Parse target category IDs and confidence threshold from node config</li>
 *   <li>Generate text embedding of the email via {@link EmbeddingService}</li>
 *   <li>Query pgvector for top-5 closest categories by cosine similarity</li>
 *   <li>Submit candidates to Gemini LLM for final classification with confidence score</li>
 *   <li>Apply threshold gating — only accept classifications above the configured minimum</li>
 *   <li>Persist accepted category assignment on the email entity (denormalized JSON)</li>
 * </ol>
 *
 * <p>A "Sonstiges" (Other) fallback category is always injected so the AI has an escape hatch
 * for emails that don't clearly match any defined category.</p>
 *
 * @since 1.0
 */
@Component
public class CategorizeNodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(CategorizeNodeExecutor.class);
    private static final String SONSTIGES_ID = "00000000-0000-0000-0000-000000000000";

    private final EmbeddingService embeddingService;
    private final GeminiService geminiService;
    private final CategoryRepository categoryRepository;
    private final EmailRepository emailRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final AttachmentContentResolver attachmentResolver;

    public CategorizeNodeExecutor(EmbeddingService embeddingService,
                                   GeminiService geminiService,
                                   CategoryRepository categoryRepository,
                                   EmailRepository emailRepository,
                                   AuditService auditService,
                                   ObjectMapper objectMapper,
                                   AttachmentContentResolver attachmentResolver) {
        this.embeddingService = embeddingService;
        this.geminiService = geminiService;
        this.categoryRepository = categoryRepository;
        this.emailRepository = emailRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.attachmentResolver = attachmentResolver;
    }

    /**
     * Result record carrying both the output handle and detailed classification info for tracing.
     */
    public record CategorizeResult(
        String outputHandle,
        ClassificationResult classificationResult,
        boolean accepted,
        int threshold,
        List<CategoryCandidate> candidates,
        UUID selectedCategoryId,
        String selectedCategoryName,
        String selectedCategoryColor
    ) {}

    public String execute(Email email, JsonNode config, UUID userId) {
        return executeDetailed(email, config, userId, false, null).outputHandle();
    }

    public CategorizeResult executeDetailed(Email email, JsonNode config, UUID userId) {
        return executeDetailed(email, config, userId, false, null);
    }

    public CategorizeResult executeDetailed(Email email, JsonNode config, UUID userId,
                                               boolean dryRun, ExecutionContext context) {
        try {
            // 1. Parse categoryIds from config
            JsonNode idsNode = config.get("categoryIds");
            if (idsNode == null || !idsNode.isArray() || idsNode.isEmpty()) {
                return new CategorizeResult("uncategorized", null, false, 0, List.of(), null, null, null);
            }

            List<UUID> categoryIds = StreamSupport.stream(idsNode.spliterator(), false)
                    .map(n -> UUID.fromString(n.asText()))
                    .collect(Collectors.toList());

            // 2. Read threshold (default 70)
            int threshold = NodeConfigReader.integer(config, "threshold", 70);

            // 3. Build email text for embedding (resolve sourceVariables if set)
            String emailText = resolveSourceVariables(config, email, context);

            // 4. Embed the email text
            float[] emailEmbedding = embeddingService.embed(context.getOrganizationId(), userId, emailText);

            // 5. Format embedding as pgvector string
            String vectorStr = formatVector(emailEmbedding);

            // 6. Query top 5 closest categories via pgvector
            List<Object[]> rows = categoryRepository.findClosestByEmbedding(categoryIds, vectorStr, 5);

            if (rows.isEmpty()) {
                return new CategorizeResult("uncategorized", null, false, threshold, List.of(), null, null, null);
            }

            // 7. Build candidate list
            List<CategoryCandidate> candidates = new ArrayList<>();
            for (Object[] row : rows) {
                candidates.add(new CategoryCandidate(
                        (UUID) row[0],
                        (String) row[1],
                        (String) row[3],
                        (String) row[4],
                        (String) row[5]
                ));
            }

            // 7b. Always add "Sonstiges" fallback so AI has an escape hatch
            candidates.add(new CategoryCandidate(
                    UUID.fromString(SONSTIGES_ID),
                    "Sonstiges",
                    "Emails that do not clearly fit into any of the above categories. Use this for newsletters, promotions, general announcements, spam, or anything ambiguous.",
                    null, null));

            // 8. Classify via Gemini (optionally with the email's attachments as inline AI input)
            List<AiAttachment> attachments = resolveAttachments(config, email, context);
            ClassificationResult result = geminiService.classify(
                    context.getOrganizationId(), userId, emailText, candidates, attachments);

            // 9. Audit log — always, regardless of threshold (skip in dry-run)
            boolean accepted = result.confidence() >= threshold
                    && !"uncategorized".equals(result.categoryId())
                    && !SONSTIGES_ID.equals(result.categoryId());
            if (!dryRun) {
                String auditDetail = buildAuditDetail(email, candidates, result, threshold, accepted);
                auditService.log(userId, AuditAction.EMAIL_CATEGORIZED, auditDetail, "system");
            }

            // 10. Resolve category details for trace
            UUID selectedCatId = null;
            String selectedCatName = null;
            String selectedCatColor = null;
            try {
                selectedCatId = UUID.fromString(result.categoryId());
                Category cat = categoryRepository.findById(selectedCatId).orElse(null);
                if (cat != null) {
                    selectedCatName = cat.getName();
                    selectedCatColor = cat.getColor();
                }
            } catch (IllegalArgumentException ignored) {}

            // 11. Apply threshold
            if (!accepted) {
                return new CategorizeResult("uncategorized", result, false, threshold,
                        candidates, selectedCatId, selectedCatName, selectedCatColor);
            }

            // 12. Write category to email entity (denormalized) — only when the email itself is the
            //      classification source (email.* source variable). For pure API/webhook text we just
            //      emit category.* downstream without stamping the triggering email. Skip in dry-run.
            if (!dryRun && email != null && sourcesIncludeEmail(config)
                    && selectedCatId != null && selectedCatName != null) {
                assignCategoryToEmail(email, selectedCatId, selectedCatName, selectedCatColor);
            }

            // 13. Map UUID to output handle (category_0, category_1, ...)
            for (int i = 0; i < categoryIds.size(); i++) {
                if (categoryIds.get(i).equals(selectedCatId)) {
                    return new CategorizeResult("category_" + i, result, true, threshold,
                            candidates, selectedCatId, selectedCatName, selectedCatColor);
                }
            }

            return new CategorizeResult("uncategorized", result, accepted, threshold,
                    candidates, selectedCatId, selectedCatName, selectedCatColor);

        } catch (Exception e) {
            log.error("Categorize node execution failed: {}", e.getMessage(), e);
            return new CategorizeResult("uncategorized", null, false, 0, List.of(), null, null, null);
        }
    }

    private void assignCategoryToEmail(Email email, UUID categoryId, String categoryName, String categoryColor) {
        try {
            List<Map<String, String>> categories;
            if (email.getCategories() != null && !email.getCategories().isBlank()) {
                categories = objectMapper.readValue(email.getCategories(), new TypeReference<>() {});
            } else {
                categories = new ArrayList<>();
            }

            // Duplicate check
            boolean exists = categories.stream()
                    .anyMatch(c -> categoryId.toString().equals(c.get("id")));
            if (!exists) {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("id", categoryId.toString());
                entry.put("name", categoryName);
                entry.put("color", categoryColor != null ? categoryColor : "#6b7280");
                categories.add(entry);
                email.setCategories(objectMapper.writeValueAsString(categories));
                emailRepository.save(email);
            }
        } catch (Exception e) {
            log.warn("Failed to assign category to email {}: {}", email.getId(), e.getMessage());
        }
    }

    private String buildAuditDetail(Email email, List<CategoryCandidate> candidates,
                                     ClassificationResult result, int threshold, boolean accepted) {
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("emailId", email.getId().toString());
            detail.put("emailSubject", email.getSubject());
            detail.put("selectedCategoryId", accepted ? result.categoryId() : "uncategorized");
            detail.put("confidence", result.confidence());
            detail.put("reason", result.reason());
            detail.put("threshold", threshold);
            detail.put("accepted", accepted);
            detail.put("candidates", candidates.stream()
                    .map(c -> Map.of("id", c.id().toString(), "name", c.name()))
                    .toList());
            return objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Feeds the email's attachments to the AI based on the selected source variables: {@code
     * email.attachments} feeds all of them; the alias of a FOREACH iterating the attachments (e.g.
     * {@code item}) feeds just the current one. Only Gemini-readable types within the size/count
     * budget are returned (see {@link AiAttachmentSupport}); the embedding/vector step stays text-only.
     * Empty when no attachment source is selected or no email/account is available.
     */
    private List<AiAttachment> resolveAttachments(JsonNode config, Email email, ExecutionContext context) {
        if (email == null || context == null || context.getAccount() == null) {
            return List.of();
        }
        List<AiAttachment> attachments = new ArrayList<>();
        for (String sv : readSourceVariables(config)) {
            AttachmentContentResolver.AttachmentSelection selection = attachmentSelectionFor(sv, context);
            if (selection == null) {
                continue;
            }
            AttachmentContentResolver.AttachmentFetchResult result =
                    attachmentResolver.fetch(context.getAccount(), email, selection);
            if (!result.skipped().isEmpty()) {
                log.info("CATEGORIZE: sent {} attachment(s) to AI, skipped {}",
                        result.fetched().size(), result.skipped().size());
            }
            attachments.addAll(AiAttachmentSupport.toAiAttachments(result));
        }
        return attachments;
    }

    /**
     * The attachment selection for a source variable, or {@code null} if it isn't an attachment
     * source: {@code email.attachments} → all; a FOREACH item alias carrying
     * {@code <alias>.__attachmentIndex} → that single attachment.
     */
    private AttachmentContentResolver.AttachmentSelection attachmentSelectionFor(String sv, ExecutionContext context) {
        if (AiAttachmentSupport.SOURCE_KEY.equals(sv)) {
            return AiAttachmentSupport.selection();
        }
        if (context != null
                && context.getVariable(sv + AiAttachmentSupport.ITEM_INDEX_SUFFIX) instanceof Number index) {
            return AiAttachmentSupport.selectionForIndex(index.intValue());
        }
        return null;
    }

    private String resolveSourceVariables(JsonNode config, Email email, ExecutionContext context) {
        List<String> vars = readSourceVariables(config);
        if (vars.isEmpty()) {
            vars.add("email.body");
        }
        StringBuilder sb = new StringBuilder();
        for (String varKey : vars) {
            if (attachmentSelectionFor(varKey, context) != null) {
                continue; // attachments (all or the current FOREACH item) are fed as binary parts, not text
            }
            if (varKey.startsWith("email.")) {
                // email.* sources: prefer a resolved context variable (e.g. email.subject),
                // otherwise fall back to the full email text representation.
                Object value = context != null ? context.getVariable(varKey) : null;
                if (value != null) {
                    sb.append(value);
                } else if (email != null) {
                    sb.append(EmailTextBuilder.build(email));
                }
            } else {
                Object value = context != null ? context.getVariable(varKey) : null;
                sb.append(value != null ? value.toString() : "");
            }
            sb.append("\n\n");
        }
        return sb.toString().trim();
    }

    /** Reads the configured source variable keys; empty list when none are set. */
    private static List<String> readSourceVariables(JsonNode config) {
        List<String> vars = new ArrayList<>();
        if (config.has("sourceVariables") && config.get("sourceVariables").isArray()) {
            for (JsonNode v : config.get("sourceVariables")) {
                vars.add(v.asText());
            }
        }
        return vars;
    }

    /**
     * Whether the classification source is the triggering email itself. True when no source
     * variables are configured (defaults to {@code email.body}) or when any configured source
     * references {@code email.*}. False for pure API/webhook text sources, which must not stamp
     * the triggering email nor require an email context.
     */
    public static boolean sourcesIncludeEmail(JsonNode config) {
        List<String> vars = readSourceVariables(config);
        if (vars.isEmpty()) {
            return true; // default source is email.body
        }
        return vars.stream().anyMatch(v -> v.startsWith("email."));
    }

    private String formatVector(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
