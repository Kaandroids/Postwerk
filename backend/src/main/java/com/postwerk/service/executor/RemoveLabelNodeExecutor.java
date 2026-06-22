package com.postwerk.service.executor;

import com.postwerk.model.Email;
import com.postwerk.repository.CategoryRepository;
import com.postwerk.repository.EmailRepository;
import com.postwerk.util.NodeConfigReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Executor for the REMOVE_LABEL node that removes category assignments from emails.
 * Updates the denormalized category JSON on the email entity.
 *
 * @since 1.0
 */
@Component
public class RemoveLabelNodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(RemoveLabelNodeExecutor.class);

    private final EmailRepository emailRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;

    public RemoveLabelNodeExecutor(EmailRepository emailRepository, CategoryRepository categoryRepository, ObjectMapper objectMapper) {
        this.emailRepository = emailRepository;
        this.categoryRepository = categoryRepository;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> execute(Email email, JsonNode config, boolean dryRun) {
        Map<String, Object> detail = new LinkedHashMap<>();

        String categoryId = NodeConfigReader.text(config, "categoryId");
        if (categoryId.isBlank()) {
            detail.put("removed", false);
            detail.put("categoryName", "");
            return detail;
        }

        var categoryOpt = categoryRepository.findById(UUID.fromString(categoryId));
        String categoryName = categoryOpt.map(c -> c.getName()).orElse(categoryId);

        detail.put("categoryId", categoryId);
        detail.put("categoryName", categoryName);

        if (!dryRun) {
            boolean removed = false;
            try {
                // Remove from labels JSON
                String labelsJson = email.getLabels();
                if (labelsJson != null && !labelsJson.isBlank()) {
                    JsonNode labelsArr = objectMapper.readTree(labelsJson);
                    if (labelsArr.isArray()) {
                        ArrayNode filtered = objectMapper.createArrayNode();
                        for (JsonNode item : labelsArr) {
                            String itemCatId = NodeConfigReader.text(item, "categoryId");
                            if (!categoryId.equals(itemCatId)) {
                                filtered.add(item);
                            } else {
                                removed = true;
                            }
                        }
                        email.setLabels(objectMapper.writeValueAsString(filtered));
                    }
                }

                // Remove from categories JSON
                String categoriesJson = email.getCategories();
                if (categoriesJson != null && !categoriesJson.isBlank()) {
                    JsonNode catsArr = objectMapper.readTree(categoriesJson);
                    if (catsArr.isArray()) {
                        ArrayNode filtered = objectMapper.createArrayNode();
                        for (JsonNode item : catsArr) {
                            String itemId = item.isTextual() ? item.asText()
                                    : NodeConfigReader.text(item, "categoryId", NodeConfigReader.text(item, "id"));
                            if (!categoryId.equals(itemId)) {
                                filtered.add(item);
                            } else {
                                removed = true;
                            }
                        }
                        email.setCategories(objectMapper.writeValueAsString(filtered));
                    }
                }

                emailRepository.save(email);
            } catch (Exception e) {
                log.error("Failed to remove label from email {}: {}", email.getId(), e.getMessage());
            }
            detail.put("removed", removed);
        } else {
            detail.put("removed", true);
        }

        return detail;
    }
}
