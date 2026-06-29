package com.postwerk.service.executor;

import com.postwerk.dto.AiAttachment;
import com.postwerk.dto.ParameterItemDto;
import com.postwerk.model.Email;
import com.postwerk.model.ParameterSet;
import com.postwerk.repository.ParameterSetRepository;
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

/**
 * Executes the EXTRACT node in an automation workflow.
 *
 * <p>For each extraction entry in the node config, loads the associated {@link ParameterSet},
 * constructs a structured extraction prompt, and delegates to Gemini AI for
 * data extraction. Results are keyed by {@code extraction_N} and fed into the
 * {@link ExecutionContext} for downstream template parameter substitution.</p>
 *
 * @since 1.0
 */
@Component
public class ExtractNodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(ExtractNodeExecutor.class);

    private final GeminiService geminiService;
    private final ParameterSetRepository parameterSetRepository;
    private final ObjectMapper objectMapper;
    private final AttachmentContentResolver attachmentResolver;

    public ExtractNodeExecutor(GeminiService geminiService, ParameterSetRepository parameterSetRepository,
                               ObjectMapper objectMapper, AttachmentContentResolver attachmentResolver) {
        this.geminiService = geminiService;
        this.parameterSetRepository = parameterSetRepository;
        this.objectMapper = objectMapper;
        this.attachmentResolver = attachmentResolver;
    }

    public Map<String, Map<String, Object>> execute(Email email, JsonNode config, UUID userId,
                                                       ExecutionContext context) {
        Map<String, Map<String, Object>> results = new LinkedHashMap<>();

        JsonNode extractions = config.has("extractions") ? config.get("extractions") : null;
        if (extractions == null || !extractions.isArray()) {
            return results;
        }

        String emailText = resolveSourceVariables(config, email, context);
        List<AiAttachment> attachments = resolveAttachments(config, email, context);

        for (int i = 0; i < extractions.size(); i++) {
            String key = "extraction_" + i;
            JsonNode extraction = extractions.get(i);

            try {
                String parameterSetId = NodeConfigReader.text(extraction, "parameterSetId", null);
                if (parameterSetId == null) {
                    results.put(key, Map.of("_error", "Missing parameterSetId"));
                    continue;
                }

                ParameterSet parameterSet = parameterSetRepository.findById(UUID.fromString(parameterSetId))
                        .orElse(null);
                if (parameterSet == null) {
                    results.put(key, Map.of("_error", "ParameterSet not found: " + parameterSetId));
                    continue;
                }

                List<ParameterItemDto> parameters = objectMapper.readValue(
                        parameterSet.getParameters(),
                        new TypeReference<>() {}
                );

                Map<String, Object> extracted = geminiService.extract(
                        context.getOrganizationId(), userId, emailText, parameters, attachments);
                results.put(key, extracted);

            } catch (Exception e) {
                log.error("Extraction {} failed: {}", key, e.getMessage());
                results.put(key, Map.of("_error", e.getMessage()));
            }
        }

        return results;
    }

    /**
     * Feeds the email's attachments to the AI based on the selected source variables: {@code
     * email.attachments} feeds all of them; the alias of a FOREACH iterating the attachments (e.g.
     * {@code item}) feeds just the current one. Only Gemini-readable types within the size/count
     * budget are returned (see {@link AiAttachmentSupport}). Empty when no attachment source is
     * selected or no email/account is available (e.g. API/webhook runs).
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
                log.info("EXTRACT: sent {} attachment(s) to AI, skipped {}",
                        result.fetched().size(), result.skipped().size());
            }
            attachments.addAll(AiAttachmentSupport.toAiAttachments(result));
        }
        return attachments;
    }

    /**
     * The attachment selection for a source variable, or {@code null} if it isn't an attachment
     * source: {@code email.attachments} → all attachments; a FOREACH item alias carrying
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
            if ("email.body".equals(varKey) || context == null) {
                sb.append(email != null ? EmailTextBuilder.build(email) : "");
            } else {
                Object value = context.getVariable(varKey);
                sb.append(value != null ? value.toString() : "");
            }
            sb.append("\n\n");
        }
        return sb.toString().trim();
    }

    /** Reads the configured source-variable keys; empty list when none are set. */
    private static List<String> readSourceVariables(JsonNode config) {
        List<String> vars = new ArrayList<>();
        if (config.has("sourceVariables") && config.get("sourceVariables").isArray()) {
            for (JsonNode v : config.get("sourceVariables")) {
                vars.add(v.asText());
            }
        }
        return vars;
    }

}
