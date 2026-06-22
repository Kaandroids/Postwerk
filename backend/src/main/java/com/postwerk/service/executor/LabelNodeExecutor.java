package com.postwerk.service.executor;

import com.postwerk.model.Email;
import com.postwerk.repository.CategoryRepository;
import com.postwerk.repository.EmailRepository;
import com.postwerk.util.NodeConfigReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Executor for the LABEL node that assigns categories to emails.
 * Persists category assignments as denormalized JSON on the email entity.
 *
 * @since 1.0
 */
@Component
public class LabelNodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(LabelNodeExecutor.class);

    private final EmailRepository emailRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;

    public LabelNodeExecutor(EmailRepository emailRepository, CategoryRepository categoryRepository, ObjectMapper objectMapper) {
        this.emailRepository = emailRepository;
        this.categoryRepository = categoryRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Assigns a category to an email via the labels JSONB field.
     * In dry-run mode, returns simulated detail without persisting.
     */
    public Map<String, Object> execute(Email email, JsonNode config, boolean dryRun) {
        Map<String, Object> detail = new LinkedHashMap<>();

        String categoryId = NodeConfigReader.text(config, "categoryId");
        if (categoryId.isBlank()) {
            detail.put("categoryName", "");
            return detail;
        }

        var categoryOpt = categoryRepository.findById(UUID.fromString(categoryId));
        String categoryName = categoryOpt.map(c -> c.getName()).orElse(categoryId);
        String categoryColor = categoryOpt.map(c -> c.getColor()).orElse("#6b7280");

        detail.put("categoryId", categoryId);
        detail.put("categoryName", categoryName);
        detail.put("categoryColor", categoryColor);

        if (!dryRun) {
            try {
                Map<String, String> labelEntry = Map.of(
                        "categoryId", categoryId,
                        "categoryName", categoryName,
                        "categoryColor", categoryColor
                );
                email.setLabels(objectMapper.writeValueAsString(List.of(labelEntry)));
                emailRepository.save(email);
            } catch (Exception e) {
                log.error("Failed to assign category to email {}: {}", email.getId(), e.getMessage());
            }
        }

        return detail;
    }
}
