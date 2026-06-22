package com.postwerk.service;

import com.postwerk.model.ConversationPhase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiToolRegistryTest {

    @Mock private CategoryService categoryService;
    @Mock private TemplateService templateService;
    @Mock private ParameterSetService parameterSetService;
    @Mock private AutomationService automationService;
    @Mock private AutomationTestService automationTestService;
    @Mock private EmailAccountService emailAccountService;
    @Mock private SecretService secretService;

    private AiToolRegistry registry;
    private UUID orgId;
    private UUID userId;
    private String ipAddress;

    private static final Set<String> AUTOMATION_WRITE_TOOLS = Set.of(
            "create_automation", "update_automation_flow", "delete_automation"
    );

    private static final Set<String> NON_GATED_TOOLS = Set.of(
            "list_categories", "create_category", "update_category", "delete_category",
            "list_templates", "create_template", "update_template", "delete_template",
            "list_parameter_sets", "create_parameter_set", "update_parameter_set", "delete_parameter_set",
            "list_automations",
            "create_automation_test", "update_automation_test", "list_automation_tests", "run_automation_tests",
            "list_email_accounts",
            "list_folders", "create_folder", "delete_folder",
            "list_secrets"
    );

    @BeforeEach
    void setUp() {
        registry = new AiToolRegistry(
                categoryService, templateService,
                parameterSetService, automationService, automationTestService,
                emailAccountService, secretService, new ObjectMapper());
        orgId = UUID.randomUUID();
        userId = UUID.randomUUID();
        ipAddress = "127.0.0.1";
    }

    // ── Helper ──────────────────────────────────────────────────────────

    private Set<String> extractToolNames(Tool tool) {
        return tool.functionDeclarations()
                .orElse(List.of())
                .stream()
                .map(fd -> fd.name().orElse(""))
                .collect(Collectors.toSet());
    }

    // ── getToolDeclarations: phase gating ───────────────────────────────

    @Test
    void openPhase_includesAllToolsIncludingAutomationWrite() {
        Set<String> names = extractToolNames(registry.getToolDeclarations(ConversationPhase.OPEN));

        assertThat(names).containsAll(AUTOMATION_WRITE_TOOLS);
        assertThat(names).containsAll(NON_GATED_TOOLS);
        assertThat(names).contains("propose_automation_plan");
    }

    @Test
    void planningPhase_excludesAutomationWriteTools() {
        Set<String> names = extractToolNames(registry.getToolDeclarations(ConversationPhase.PLANNING));

        assertThat(names).doesNotContainAnyElementsOf(AUTOMATION_WRITE_TOOLS);
    }

    @Test
    void planningPhase_excludesProposeAutomationPlan() {
        Set<String> names = extractToolNames(registry.getToolDeclarations(ConversationPhase.PLANNING));

        assertThat(names).doesNotContain("propose_automation_plan");
    }

    @Test
    void planningPhase_includesAllNonGatedTools() {
        Set<String> names = extractToolNames(registry.getToolDeclarations(ConversationPhase.PLANNING));

        assertThat(names).containsAll(NON_GATED_TOOLS);
    }

    @Test
    void buildingPhase_includesAutomationWriteTools() {
        Set<String> names = extractToolNames(registry.getToolDeclarations(ConversationPhase.BUILDING));

        assertThat(names).containsAll(AUTOMATION_WRITE_TOOLS);
        assertThat(names).containsAll(NON_GATED_TOOLS);
    }

    @Test
    void buildingPhase_excludesProposeAutomationPlan() {
        Set<String> names = extractToolNames(registry.getToolDeclarations(ConversationPhase.BUILDING));

        assertThat(names).doesNotContain("propose_automation_plan");
    }

    @Test
    void noArgOverload_returnsSameAsOpen() {
        Set<String> noArg = extractToolNames(registry.getToolDeclarations());
        Set<String> open = extractToolNames(registry.getToolDeclarations(ConversationPhase.OPEN));

        assertThat(noArg).isEqualTo(open);
    }

    // ── isAutomationWriteTool ───────────────────────────────────────────

    @Test
    void isAutomationWriteTool_returnsTrueForGatedTools() {
        assertThat(registry.isAutomationWriteTool("create_automation")).isTrue();
        assertThat(registry.isAutomationWriteTool("update_automation_flow")).isTrue();
        assertThat(registry.isAutomationWriteTool("delete_automation")).isTrue();
    }

    @Test
    void isAutomationWriteTool_returnsFalseForNonGatedTools() {
        assertThat(registry.isAutomationWriteTool("list_categories")).isFalse();
        assertThat(registry.isAutomationWriteTool("create_filter")).isFalse();
        assertThat(registry.isAutomationWriteTool("list_automations")).isFalse();
        assertThat(registry.isAutomationWriteTool("propose_automation_plan")).isFalse();
    }

    // ── executeTool: PLANNING phase blocks automation writes ────────────

    @Test
    void executeTool_planningPhase_blocksCreateAutomation() {
        Map<String, Object> result = registry.executeTool(
                "create_automation", Map.of(), orgId, userId, ipAddress, ConversationPhase.PLANNING);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat((String) result.get("error")).contains("planning phase");
    }

    @Test
    void executeTool_planningPhase_blocksUpdateAutomationFlow() {
        Map<String, Object> result = registry.executeTool(
                "update_automation_flow", Map.of(), orgId, userId, ipAddress, ConversationPhase.PLANNING);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat((String) result.get("error")).contains("planning phase");
    }

    @Test
    void executeTool_planningPhase_blocksDeleteAutomation() {
        Map<String, Object> result = registry.executeTool(
                "delete_automation", Map.of(), orgId, userId, ipAddress, ConversationPhase.PLANNING);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat((String) result.get("error")).contains("planning phase");
    }

    // ── executeTool: non-gated tools work in all phases ─────────────────

    @Test
    void executeTool_planningPhase_allowsListCategories() {
        when(categoryService.listByOrg(orgId)).thenReturn(List.of());

        Map<String, Object> result = registry.executeTool(
                "list_categories", Map.of(), orgId, userId, ipAddress, ConversationPhase.PLANNING);

        assertThat(result.get("success")).isEqualTo(true);
        verify(categoryService).listByOrg(orgId);
    }

    @Test
    void executeTool_openPhase_allowsListCategories() {
        when(categoryService.listByOrg(orgId)).thenReturn(List.of());

        Map<String, Object> result = registry.executeTool(
                "list_categories", Map.of(), orgId, userId, ipAddress, ConversationPhase.OPEN);

        assertThat(result.get("success")).isEqualTo(true);
        verify(categoryService).listByOrg(orgId);
    }

    @Test
    void executeTool_buildingPhase_allowsListCategories() {
        when(categoryService.listByOrg(orgId)).thenReturn(List.of());

        Map<String, Object> result = registry.executeTool(
                "list_categories", Map.of(), orgId, userId, ipAddress, ConversationPhase.BUILDING);

        assertThat(result.get("success")).isEqualTo(true);
        verify(categoryService).listByOrg(orgId);
    }

    @Test
    void executeTool_planningPhase_allowsListSecrets() {
        when(secretService.list(orgId)).thenReturn(List.of());

        Map<String, Object> result = registry.executeTool(
                "list_secrets", Map.of(), orgId, userId, ipAddress, ConversationPhase.PLANNING);

        assertThat(result.get("success")).isEqualTo(true);
        verify(secretService).list(orgId);
    }

    @Test
    void executeTool_planningPhase_allowsListAutomations() {
        when(automationService.listByOrg(orgId)).thenReturn(List.of());

        Map<String, Object> result = registry.executeTool(
                "list_automations", Map.of(), orgId, userId, ipAddress, ConversationPhase.PLANNING);

        assertThat(result.get("success")).isEqualTo(true);
        verify(automationService).listByOrg(orgId);
    }

    // ── executeTool: BUILDING phase allows automation writes ────────────

    @Test
    void executeTool_buildingPhase_allowsDeleteAutomation() {
        UUID automationId = UUID.randomUUID();
        when(automationService.isLocked(orgId, automationId)).thenReturn(false);
        doNothing().when(automationService).delete(orgId, userId, automationId, ipAddress);

        Map<String, Object> result = registry.executeTool(
                "delete_automation",
                Map.of("id", automationId.toString()),
                orgId, userId, ipAddress, ConversationPhase.BUILDING);

        assertThat(result.get("success")).isEqualTo(true);
        verify(automationService).delete(orgId, userId, automationId, ipAddress);
    }

    @Test
    void executeTool_openPhase_allowsDeleteAutomation() {
        UUID automationId = UUID.randomUUID();
        when(automationService.isLocked(orgId, automationId)).thenReturn(false);
        doNothing().when(automationService).delete(orgId, userId, automationId, ipAddress);

        Map<String, Object> result = registry.executeTool(
                "delete_automation",
                Map.of("id", automationId.toString()),
                orgId, userId, ipAddress, ConversationPhase.OPEN);

        assertThat(result.get("success")).isEqualTo(true);
        verify(automationService).delete(orgId, userId, automationId, ipAddress);
    }

    // ── executeTool: propose_automation_plan ─────────────────────────────

    @Test
    void executeTool_proposeAutomationPlan_returnsProposed() {
        Map<String, Object> result = registry.executeTool(
                "propose_automation_plan",
                Map.of("planSummary", "Create a categorization automation"),
                orgId, userId, ipAddress, ConversationPhase.OPEN);

        assertThat(result.get("success")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertThat(data.get("proposed")).isEqualTo(true);
        assertThat(data.get("planSummary")).isEqualTo("Create a categorization automation");
    }

    // ── executeTool: unknown tool ───────────────────────────────────────

    @Test
    void executeTool_unknownTool_returnsError() {
        Map<String, Object> result = registry.executeTool(
                "nonexistent_tool", Map.of(), orgId, userId, ipAddress, ConversationPhase.OPEN);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat((String) result.get("error")).contains("Unknown tool");
    }

    // ── executeTool: no-arg overload defaults to OPEN ───────────────────

    @Test
    void executeTool_noPhaseOverload_defaultsToOpen() {
        when(categoryService.listByOrg(orgId)).thenReturn(List.of());

        Map<String, Object> result = registry.executeTool(
                "list_categories", Map.of(), orgId, userId, ipAddress);

        assertThat(result.get("success")).isEqualTo(true);
        verify(categoryService).listByOrg(orgId);
    }

    // ── executeTool: locked resource returns error ──────────────────────

    @Test
    void executeTool_lockedAutomation_returnsError() {
        UUID automationId = UUID.randomUUID();
        when(automationService.isLocked(orgId, automationId)).thenReturn(true);

        Map<String, Object> result = registry.executeTool(
                "delete_automation",
                Map.of("id", automationId.toString()),
                orgId, userId, ipAddress, ConversationPhase.OPEN);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat((String) result.get("error")).contains("locked");
    }

    // ── executeTool: service exception is caught ────────────────────────

    @Test
    void executeTool_serviceThrows_returnsError() {
        when(categoryService.listByOrg(orgId)).thenThrow(new RuntimeException("DB down"));

        Map<String, Object> result = registry.executeTool(
                "list_categories", Map.of(), orgId, userId, ipAddress, ConversationPhase.OPEN);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat((String) result.get("error")).contains("DB down");
    }
}
