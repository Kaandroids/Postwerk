package com.postwerk.service;

import com.postwerk.model.ConversationPhase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the dynamic, phase-aware system prompt for the AI assistant.
 *
 * <p>Combines the static reference templates with a JSON snapshot of the user's current resources
 * and a phase-specific instruction block. Extracted from the assistant service to isolate prompt
 * assembly; behaviour is unchanged from the original inline helper.</p>
 *
 * @since 1.0
 */
@Component
public class SystemPromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(SystemPromptBuilder.class);

    private final PromptService promptService;
    private final CategoryService categoryService;
    private final TemplateService templateService;
    private final ParameterSetService parameterSetService;
    private final AutomationService automationService;
    private final EmailAccountService emailAccountService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ObjectMapper objectMapper;

    public SystemPromptBuilder(PromptService promptService,
                               CategoryService categoryService,
                               TemplateService templateService,
                               ParameterSetService parameterSetService,
                               AutomationService automationService,
                               EmailAccountService emailAccountService,
                               KnowledgeBaseService knowledgeBaseService,
                               ObjectMapper objectMapper) {
        this.promptService = promptService;
        this.categoryService = categoryService;
        this.templateService = templateService;
        this.parameterSetService = parameterSetService;
        this.automationService = automationService;
        this.emailAccountService = emailAccountService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.objectMapper = objectMapper;
    }

    public String build(UUID userId, UUID organizationId, ConversationPhase phase, String language) {
        StringBuilder sb = new StringBuilder();

        // Static reference manual and tool documentation from template files
        sb.append(promptService.load("assistant-system.txt"));
        sb.append("\n\n");
        sb.append(promptService.load("tools-reference.txt"));
        sb.append("\n\n");
        // Shared node-config reference (same source the onboarding wizard uses)
        sb.append(promptService.load("node-config-reference.txt"));

        // Pin the reply language to the user's UI language. Short button presses (e.g. the
        // one-word "Build automation" suggestion) are unreliable to language-detect, so the
        // interface language wins by default and only an explicit message in another language overrides it.
        String langDirective = languageDirective(language);
        if (langDirective != null) {
            sb.append("\n\n═══════════════════════════════════════════════════════════\n");
            sb.append("RESPONSE LANGUAGE\n");
            sb.append("═══════════════════════════════════════════════════════════\n");
            sb.append(langDirective);
        }

        // Inject dynamic user resources
        sb.append("\n\n═══════════════════════════════════════════════════════════\n");
        sb.append("USER'S CURRENT RESOURCES\n");
        sb.append("═══════════════════════════════════════════════════════════\n");
        try {
            Map<String, Object> resources = new LinkedHashMap<>();
            resources.put("categories", categoryService.listByOrg(organizationId));
            // email_filters system removed — filters are now inline in automation FILTER nodes
            resources.put("templates", templateService.listByOrg(organizationId));
            resources.put("parameterSets", parameterSetService.listByOrg(organizationId));
            resources.put("automations", automationService.listByOrg(organizationId));
            resources.put("emailAccounts", emailAccountService.listByOrg(organizationId));
            // Knowledge bases the VECTOR_SEARCH node searches (id + name + parameterSetId).
            resources.put("knowledgeBases", knowledgeBaseService.listByOrg(organizationId));
            sb.append(objectMapper.writeValueAsString(resources));
        } catch (Exception e) {
            log.warn("Failed to inject user resources into system prompt", e);
            sb.append("{}");
        }

        // Inject current conversation phase
        sb.append("\n\n═══════════════════════════════════════════════════════════\n");
        sb.append("CURRENT CONVERSATION PHASE: ").append(phase.name()).append("\n");
        sb.append("═══════════════════════════════════════════════════════════\n");
        switch (phase) {
            case OPEN -> sb.append("""
                    You are in OPEN phase. All tools are available. \
                    If the user asks for an automation, call `propose_automation_plan` to enter planning mode. \
                    For non-automation tasks (categories, filters, templates), just create them directly.""");
            case PLANNING -> sb.append("""
                    You are in PLANNING phase. Automation write tools (create_automation, update_automation_flow, \
                    delete_automation) are NOT available right now. \
                    You already proposed a plan. Present your detailed plan to the user and wait for their confirmation. \
                    You CAN still create dependencies (categories, filters, templates, parameter sets) during this phase. \
                    Do NOT try to call create_automation or update_automation_flow — they will fail.""");
            case BUILDING -> sb.append("""
                    You are in BUILDING phase. The user confirmed your plan. ALL tools are available. \
                    Proceed immediately to build the automation: create dependencies, create_automation, \
                    update_automation_flow, then create tests. Do NOT re-propose or re-ask — just build.""");
        }
        sb.append("\n");

        return sb.toString();
    }

    /**
     * Maps a UI language code to an explicit reply-language directive, or {@code null} when no
     * (or an unknown) language is supplied — in which case the static "detect from the message"
     * rule in {@code assistant-system.txt} remains the only guidance.
     */
    private static String languageDirective(String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        String name = switch (language.toLowerCase().split("[-_]")[0]) {
            case "de" -> "German";
            case "en" -> "English";
            default -> null;
        };
        if (name == null) {
            return null;
        }
        return "The user's interface is set to " + name + ". Reply in " + name + " by default — "
                + "including your very first reply, plan presentations, and confirmations — even when the "
                + "user's message is very short (such as a one- or two-word button press like \"Build automation\"). "
                + "Only reply in another language if the user deliberately writes a full message in that language.";
    }
}
