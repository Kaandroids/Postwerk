package com.postwerk.service;

import com.postwerk.dto.*;
import com.postwerk.dto.automation.AutomationTestCaseRequest;
import com.postwerk.dto.automation.TestAssertion;
import com.postwerk.dto.automation.TestEmailInput;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import com.google.genai.types.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

import com.postwerk.model.ConversationPhase;

/**
 * Registry that defines and executes AI function-calling tools for the Gemini-powered assistant.
 * Maps tool declarations to service operations for categories, templates,
 * parameter sets, automations, and email accounts.
 *
 * @since 1.0
 */
@Component
public class AiToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(AiToolRegistry.class);

    private final CategoryService categoryService;
    private final TemplateService templateService;
    private final ParameterSetService parameterSetService;
    private final AutomationService automationService;
    private final AutomationTestService automationTestService;
    private final EmailAccountService emailAccountService;
    private final SecretService secretService;
    private final ObjectMapper objectMapper;

    public AiToolRegistry(CategoryService categoryService,
                          TemplateService templateService,
                          ParameterSetService parameterSetService,
                          AutomationService automationService,
                          AutomationTestService automationTestService,
                          EmailAccountService emailAccountService,
                          SecretService secretService,
                          ObjectMapper objectMapper) {
        this.categoryService = categoryService;
        this.templateService = templateService;
        this.parameterSetService = parameterSetService;
        this.automationService = automationService;
        this.automationTestService = automationTestService;
        this.emailAccountService = emailAccountService;
        this.secretService = secretService;
        this.objectMapper = objectMapper;
    }

    private static final Set<String> AUTOMATION_WRITE_TOOLS = Set.of(
            "create_automation", "update_automation_flow", "delete_automation"
    );

    /**
     * Returns all tool declarations (no gating). Used for OPEN and BUILDING phases.
     */
    public Tool getToolDeclarations() {
        return getToolDeclarations(ConversationPhase.OPEN);
    }

    /**
     * Returns tool declarations gated by conversation phase.
     * During PLANNING, automation write tools are excluded and propose_automation_plan is omitted
     * (since the plan was already proposed). During OPEN, propose_automation_plan is included.
     */
    public Tool getToolDeclarations(ConversationPhase phase) {
        List<FunctionDeclaration> base = new ArrayList<>(List.of(
                // Categories
                listCategories(),
                AiToolDeclarations.createCategory(),
                updateCategory(),
                deleteCategory(),
                // Templates
                listTemplates(),
                AiToolDeclarations.createTemplate(),
                updateTemplate(),
                deleteTemplate(),
                // Parameter Sets
                listParameterSets(),
                AiToolDeclarations.createParameterSet(),
                updateParameterSet(),
                deleteParameterSet(),
                // Automations (read)
                listAutomations(),
                // Automation Tests
                createAutomationTest(),
                updateAutomationTest(),
                listAutomationTests(),
                runAutomationTests(),
                // Email Accounts
                listEmailAccounts(),
                // Folders
                listFolders(),
                createFolder(),
                deleteFolder(),
                // Secrets
                listSecrets()
        ));

        if (phase != ConversationPhase.PLANNING) {
            // Automation write tools only available outside PLANNING
            base.add(AiToolDeclarations.createAutomation());
            base.add(updateAutomation());
            base.add(AiToolDeclarations.updateAutomationFlow());
            base.add(deleteAutomation());
        }

        if (phase == ConversationPhase.OPEN) {
            // propose_automation_plan only available in OPEN (triggers transition to PLANNING)
            base.add(proposeAutomationPlan());
        }

        return Tool.builder().functionDeclarations(base).build();
    }

    /**
     * Returns true if the given tool name is gated (blocked during PLANNING phase).
     */
    public boolean isAutomationWriteTool(String toolName) {
        return AUTOMATION_WRITE_TOOLS.contains(toolName);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> executeTool(String name, Map<String, Object> args, UUID organizationId, UUID userId, String ipAddress) {
        return executeTool(name, args, organizationId, userId, ipAddress, ConversationPhase.OPEN);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> executeTool(String name, Map<String, Object> args, UUID organizationId, UUID userId, String ipAddress, ConversationPhase phase) {
        // Guard: block automation write tools during PLANNING phase
        if (phase == ConversationPhase.PLANNING && AUTOMATION_WRITE_TOOLS.contains(name)) {
            return Map.of("success", false, "error",
                    "Cannot create/modify automations during planning phase. Wait for user confirmation.");
        }

        // Guard: block propose_automation_plan during PLANNING/BUILDING (already proposed)
        if ("propose_automation_plan".equals(name) && phase != ConversationPhase.OPEN) {
            return Map.of("success", false, "error",
                    "Cannot propose a new plan while already in " + phase.name() + " phase. " +
                    "Complete or cancel the current plan first.");
        }

        // Holder for the automation id whose flow was just replaced; non-null â†’ attach validation issues.
        final UUID[] flowValidationId = {null};
        try {
            Object result = switch (name) {
                case "propose_automation_plan" -> {
                    String planSummary = (String) args.getOrDefault("planSummary", "");
                    yield Map.of("proposed", true, "planSummary", planSummary,
                            "message", "Planning mode activated. Now present your FULL detailed plan to the user in your text response. " +
                                    "List every node, every dependency, filter conditions, template content, etc. " +
                                    "Then ask for confirmation. Automation write tools are locked until the user confirms.");
                }
                case "list_categories" -> categoryService.listByOrg(organizationId);
                case "create_category" -> categoryService.create(organizationId, userId, convertTo(args, CategoryRequest.class), ipAddress);
                case "update_category" -> updateResource(categoryService, CategoryRequest.class, "category", args, organizationId, userId, ipAddress);
                case "delete_category" -> deleteResource(categoryService, "category", args, organizationId, userId, ipAddress);
                case "list_templates" -> templateService.listByOrg(organizationId);
                case "create_template" -> templateService.create(organizationId, userId, convertTo(args, TemplateRequest.class), ipAddress);
                case "update_template" -> updateResource(templateService, TemplateRequest.class, "template", args, organizationId, userId, ipAddress);
                case "delete_template" -> deleteResource(templateService, "template", args, organizationId, userId, ipAddress);
                case "list_parameter_sets" -> parameterSetService.listByOrg(organizationId);
                case "create_parameter_set" -> parameterSetService.create(organizationId, userId, convertTo(args, ParameterSetRequest.class), ipAddress);
                case "update_parameter_set" -> updateResource(parameterSetService, ParameterSetRequest.class, "parameter set", args, organizationId, userId, ipAddress);
                case "delete_parameter_set" -> deleteResource(parameterSetService, "parameter set", args, organizationId, userId, ipAddress);
                case "list_automations" -> automationService.listByOrg(organizationId);
                case "create_automation" -> automationService.create(organizationId, userId, convertTo(args, AutomationRequest.class), ipAddress);
                case "update_automation" -> {
                    UUID id = UUID.fromString((String) args.get("id"));
                    checkNotLocked(automationService.isLocked(organizationId, id), "automation");
                    Map<String, Object> fields = new HashMap<>(args);
                    fields.remove("id");
                    yield automationService.update(organizationId, userId, id, convertTo(fields, AutomationRequest.class), ipAddress);
                }
                case "update_automation_flow" -> {
                    UUID automationId = UUID.fromString((String) args.get("automationId"));
                    checkNotLocked(automationService.isLocked(organizationId, automationId), "automation");
                    FlowUpdateRequest flowReq = buildFlowUpdateRequest(args);
                    var detail = automationService.updateFlow(organizationId, userId, automationId, flowReq, ipAddress);
                    flowValidationId[0] = automationId;
                    yield detail;
                }
                case "delete_automation" -> { UUID id = UUID.fromString((String) args.get("id")); checkNotLocked(automationService.isLocked(organizationId, id), "automation"); automationService.delete(organizationId, userId, id, ipAddress); yield Map.of("deleted", true); }
                case "create_automation_test" -> {
                    UUID automationId = UUID.fromString((String) args.get("automationId"));
                    yield automationTestService.createTestCase(organizationId, automationId, buildTestCaseRequest(args));
                }
                case "update_automation_test" -> {
                    UUID automationId = UUID.fromString((String) args.get("automationId"));
                    UUID testCaseId = UUID.fromString((String) args.get("testCaseId"));
                    Map<String, Object> fields = new HashMap<>(args);
                    fields.remove("testCaseId");
                    yield automationTestService.updateTestCase(organizationId, automationId, testCaseId, buildTestCaseRequest(fields));
                }
                case "list_automation_tests" -> automationTestService.getTestCases(organizationId, UUID.fromString((String) args.get("automationId")));
                case "run_automation_tests" -> automationTestService.runAllTests(organizationId, UUID.fromString((String) args.get("automationId")));
                case "list_email_accounts" -> emailAccountService.listByOrg(organizationId);
                case "list_folders" -> emailAccountService.listFolders(organizationId, UUID.fromString((String) args.get("accountId")));
                case "create_folder" -> emailAccountService.createFolder(organizationId, UUID.fromString((String) args.get("accountId")), (String) args.get("folderName"));
                case "delete_folder" -> { emailAccountService.deleteFolder(organizationId, UUID.fromString((String) args.get("accountId")), UUID.fromString((String) args.get("folderId"))); yield Map.of("deleted", true); }
                case "list_secrets" -> secretService.list(organizationId);
                default -> throw new IllegalArgumentException("Unknown tool: " + name);
            };
            if (flowValidationId[0] != null) {
                var validation = automationService.validate(organizationId, flowValidationId[0]);
                if (!validation.issues().isEmpty()) {
                    return Map.of("success", true, "data", result, "validationIssues", validation.issues());
                }
            }
            return Map.of("success", true, "data", result);
        } catch (Exception e) {
            log.warn("Tool execution failed: {} - {}", name, e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    private void checkNotLocked(boolean locked, String resourceType) {
        if (locked) {
            throw new IllegalStateException("This " + resourceType + " is locked and cannot be modified by AI. The user must unlock it first from the UI.");
        }
    }

    private <T> T convertTo(Map<String, Object> args, Class<T> clazz) {
        return objectMapper.convertValue(args, clazz);
    }

    /** Shared body for the {@code update_*} CRUD tools: id parse + lock check + convert + update. */
    private <Req, Resp, Exp> Resp updateResource(OrgScopedCrudService<Req, Resp, Exp> service, Class<Req> reqClass,
                                                 String label, Map<String, Object> args,
                                                 UUID organizationId, UUID userId, String ipAddress) {
        UUID id = UUID.fromString((String) args.get("id"));
        checkNotLocked(service.isLocked(organizationId, id), label);
        Map<String, Object> fields = new HashMap<>(args);
        fields.remove("id");
        return service.update(organizationId, userId, id, convertTo(fields, reqClass), ipAddress);
    }

    /** Shared body for the {@code delete_*} CRUD tools: id parse + lock check + delete. */
    private Map<String, Object> deleteResource(OrgScopedCrudService<?, ?, ?> service, String label,
                                               Map<String, Object> args,
                                               UUID organizationId, UUID userId, String ipAddress) {
        UUID id = UUID.fromString((String) args.get("id"));
        checkNotLocked(service.isLocked(organizationId, id), label);
        service.delete(organizationId, userId, id, ipAddress);
        return Map.of("deleted", true);
    }

    @SuppressWarnings("unchecked")
    private FlowUpdateRequest buildFlowUpdateRequest(Map<String, Object> args) {
        Object nodesObj = args.get("nodes");
        Object edgesObj = args.get("edges");
        String viewport = (String) args.get("viewport");

        List<FlowUpdateRequest.FlowNodeRequest> nodes = objectMapper.convertValue(
                nodesObj, new TypeReference<>() {});
        List<FlowUpdateRequest.FlowEdgeRequest> edges = objectMapper.convertValue(
                edgesObj, new TypeReference<>() {});

        return new FlowUpdateRequest(nodes, edges, viewport);
    }

    // â”€â”€â”€ Tool Declarations â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private FunctionDeclaration proposeAutomationPlan() {
        return FunctionDeclaration.builder()
                .name("propose_automation_plan")
                .description("Call this BEFORE presenting your automation plan. This activates planning mode â€” " +
                        "automation write tools become available only after the user confirms. " +
                        "After calling this tool, you MUST present the full detailed plan in your text response.")
                .parameters(Schema.builder()
                        .type("OBJECT")
                        .properties(Map.of(
                                "planSummary", Schema.builder().type("STRING")
                                        .description("Detailed summary of the automation plan: what nodes will be created " +
                                                "(trigger, filter conditions, categories, extract fields, actions), " +
                                                "what dependencies are needed (filters, templates, parameter sets, categories), " +
                                                "and the overall flow. Be specific, not generic.")
                                        .build()
                        ))
                        .required(List.of("planSummary"))
                        .build())
                .build();
    }

    private FunctionDeclaration listCategories() {
        return FunctionDeclaration.builder()
                .name("list_categories")
                .description("List all categories for the current user")
                .parameters(Schema.builder().type("OBJECT").properties(Map.of()).build())
                .build();
    }

    private FunctionDeclaration updateCategory() {
        return FunctionDeclaration.builder()
                .name("update_category")
                .description("Update an existing category by its ID")
                .parameters(Schema.builder()
                        .type("OBJECT")
                        .properties(Map.of(
                                "id", Schema.builder().type("STRING").description("Category UUID to update").build(),
                                "name", Schema.builder().type("STRING").description("Category name (min 3 chars)").build(),
                                "color", Schema.builder().type("STRING").description("Hex color code, e.g. #3B82F6").build(),
                                "description", Schema.builder().type("STRING").description("Detailed description of the category (min 30 chars)").build(),
                                "positiveExample", Schema.builder().type("STRING").description("Example email that belongs to this category").build(),
                                "negativeExample", Schema.builder().type("STRING").description("Example email that does NOT belong to this category").build()
                        ))
                        .required(List.of("id", "name", "color", "description"))
                        .build())
                .build();
    }

    private FunctionDeclaration deleteCategory() {
        return FunctionDeclaration.builder()
                .name("delete_category")
                .description("Delete a category by its ID")
                .parameters(Schema.builder()
                        .type("OBJECT")
                        .properties(Map.of("id", Schema.builder().type("STRING").description("Category UUID").build()))
                        .required(List.of("id"))
                        .build())
                .build();
    }

    private FunctionDeclaration listTemplates() {
        return FunctionDeclaration.builder()
                .name("list_templates")
                .description("List all email templates for the current user")
                .parameters(Schema.builder().type("OBJECT").properties(Map.of()).build())
                .build();
    }

    private FunctionDeclaration updateTemplate() {
        return FunctionDeclaration.builder()
                .name("update_template")
                .description("Update an existing email template by its ID")
                .parameters(Schema.builder()
                        .type("OBJECT")
                        .properties(Map.of(
                                "id", Schema.builder().type("STRING").description("Template UUID to update").build(),
                                "name", Schema.builder().type("STRING").description("Template name").build(),
                                "subject", Schema.builder().type("STRING").description("Email subject line").build(),
                                "body", Schema.builder().type("STRING").description("Email body as rich HTML with {{placeholder}} variables").build(),
                                "parameterSetId", Schema.builder().type("STRING").description("Parameter set UUID to link").build()
                        ))
                        .required(List.of("id", "name", "subject", "body"))
                        .build())
                .build();
    }

    private FunctionDeclaration deleteTemplate() {
        return FunctionDeclaration.builder()
                .name("delete_template")
                .description("Delete a template by its ID")
                .parameters(Schema.builder()
                        .type("OBJECT")
                        .properties(Map.of("id", Schema.builder().type("STRING").description("Template UUID").build()))
                        .required(List.of("id"))
                        .build())
                .build();
    }

    private FunctionDeclaration listParameterSets() {
        return FunctionDeclaration.builder()
                .name("list_parameter_sets")
                .description("List all parameter sets (extraction schemas) for the current user")
                .parameters(Schema.builder().type("OBJECT").properties(Map.of()).build())
                .build();
    }

    private FunctionDeclaration updateParameterSet() {
        Schema paramSchema = Schema.builder()
                .type("OBJECT")
                .properties(Map.of(
                        "name", Schema.builder().type("STRING").description("Parameter name").build(),
                        "type", Schema.builder().type("STRING").description("Type: TEXT, NUMBER, BOOLEAN, EMAIL, DATE, OBJECT").build(),
                        "description", Schema.builder().type("STRING").description("What this parameter represents").build(),
                        "positiveExample", Schema.builder().type("STRING").description("Example value").build(),
                        "negativeExample", Schema.builder().type("STRING").description("Counter-example value").build(),
                        "isList", Schema.builder().type("BOOLEAN").description("Whether this parameter extracts as array").build(),
                        "required", Schema.builder().type("BOOLEAN").description("Whether this parameter is required").build()
                ))
                .required(List.of("name", "type"))
                .build();

        return FunctionDeclaration.builder()
                .name("update_parameter_set")
                .description("Update an existing parameter set by its ID")
                .parameters(Schema.builder()
                        .type("OBJECT")
                        .properties(Map.of(
                                "id", Schema.builder().type("STRING").description("Parameter set UUID to update").build(),
                                "name", Schema.builder().type("STRING").description("Parameter set name (min 3 chars)").build(),
                                "parameters", Schema.builder().type("ARRAY").items(paramSchema).description("List of parameters to extract").build()
                        ))
                        .required(List.of("id", "name", "parameters"))
                        .build())
                .build();
    }

    private FunctionDeclaration deleteParameterSet() {
        return FunctionDeclaration.builder()
                .name("delete_parameter_set")
                .description("Delete a parameter set by its ID")
                .parameters(Schema.builder()
                        .type("OBJECT")
                        .properties(Map.of("id", Schema.builder().type("STRING").description("Parameter set UUID").build()))
                        .required(List.of("id"))
                        .build())
                .build();
    }

    private FunctionDeclaration listAutomations() {
        return FunctionDeclaration.builder()
                .name("list_automations")
                .description("List all automations for the current user")
                .parameters(Schema.builder().type("OBJECT").properties(Map.of()).build())
                .build();
    }

    private FunctionDeclaration updateAutomation() {
        return FunctionDeclaration.builder()
                .name("update_automation")
                .description("Update an existing automation's metadata (name, description, color) by its ID")
                .parameters(Schema.builder()
                        .type("OBJECT")
                        .properties(Map.of(
                                "id", Schema.builder().type("STRING").description("Automation UUID to update").build(),
                                "name", Schema.builder().type("STRING").description("Automation name").build(),
                                "description", Schema.builder().type("STRING").description("What this automation does").build(),
                                "color", Schema.builder().type("STRING").description("Hex color code").build()
                        ))
                        .required(List.of("id", "name"))
                        .build())
                .build();
    }

    private FunctionDeclaration deleteAutomation() {
        return FunctionDeclaration.builder()
                .name("delete_automation")
                .description("Delete an automation by its ID")
                .parameters(Schema.builder()
                        .type("OBJECT")
                        .properties(Map.of("id", Schema.builder().type("STRING").description("Automation UUID").build()))
                        .required(List.of("id"))
                        .build())
                .build();
    }

    private FunctionDeclaration listEmailAccounts() {
        return FunctionDeclaration.builder()
                .name("list_email_accounts")
                .description("List all email accounts for the current user")
                .parameters(Schema.builder().type("OBJECT").properties(Map.of()).build())
                .build();
    }

    private FunctionDeclaration listFolders() {
        return FunctionDeclaration.builder()
                .name("list_folders")
                .description("List all IMAP folders for a given email account")
                .parameters(Schema.builder()
                        .type("OBJECT")
                        .properties(Map.of(
                                "accountId", Schema.builder().type("STRING").description("Email account UUID").build()
                        ))
                        .required(List.of("accountId"))
                        .build())
                .build();
    }

    private FunctionDeclaration createFolder() {
        return FunctionDeclaration.builder()
                .name("create_folder")
                .description("Create a new IMAP folder on the mail server for a given email account")
                .parameters(Schema.builder()
                        .type("OBJECT")
                        .properties(Map.of(
                                "accountId", Schema.builder().type("STRING").description("Email account UUID").build(),
                                "folderName", Schema.builder().type("STRING").description("Name of the folder to create").build()
                        ))
                        .required(List.of("accountId", "folderName"))
                        .build())
                .build();
    }

    private FunctionDeclaration deleteFolder() {
        return FunctionDeclaration.builder()
                .name("delete_folder")
                .description("Delete a custom IMAP folder from the mail server. System folders (Inbox, Sent, Trash, etc.) cannot be deleted.")
                .parameters(Schema.builder()
                        .type("OBJECT")
                        .properties(Map.of(
                                "accountId", Schema.builder().type("STRING").description("Email account UUID").build(),
                                "folderId", Schema.builder().type("STRING").description("Folder UUID to delete (from list_folders)").build()
                        ))
                        .required(List.of("accountId", "folderId"))
                        .build())
                .build();
    }

    private FunctionDeclaration listSecrets() {
        return FunctionDeclaration.builder()
                .name("list_secrets")
                .description("List all secrets (name and description only, values are never exposed). Secrets can be referenced by ID in webhook nodes for authentication.")
                .parameters(Schema.builder().type("OBJECT").properties(Map.of()).build())
                .build();
    }

    private FunctionDeclaration createAutomationTest() {
        Schema assertionSchema = Schema.builder()
                .type("OBJECT")
                .properties(Map.of(
                        "nodeId", Schema.builder().type("STRING").description("UUID of the node to assert on (from update_automation_flow response)").build(),
                        "expectedStatus", Schema.builder().type("STRING").description("Expected status: PASSED, MATCHED, NOT_MATCHED, EXTRACTED, CATEGORIZED, EXECUTED, SIMULATED, SKIPPED").build(),
                        "field", Schema.builder().type("STRING").description("Optional: specific field in resultDetail to check").build(),
                        "expectedValue", Schema.builder().type("STRING").description("Optional: expected value for that field").build()
                ))
                .required(List.of("nodeId", "expectedStatus"))
                .build();

        return FunctionDeclaration.builder()
                .name("create_automation_test")
                .description("Create a test case for an automation. The test runs in dry-run mode (no real emails sent). "
                        + "Provide a synthetic email input and assertions per node to verify the automation logic works correctly. "
                        + "EMAIL_ACTION/LABEL/REMOVE_LABEL/DELAY nodes get SIMULATED status (not actually executed). Max 20 tests per automation.")
                .parameters(Schema.builder()
                        .type("OBJECT")
                        .properties(Map.of(
                                "automationId", Schema.builder().type("STRING").description("Automation UUID to add the test to").build(),
                                "name", Schema.builder().type("STRING").description("Test case name (e.g. 'Invoice email should be forwarded')").build(),
                                "description", Schema.builder().type("STRING").description("Optional description of what this test verifies").build(),
                                "from", Schema.builder().type("STRING").description("Sender email address for the synthetic test email").build(),
                                "to", Schema.builder().type("STRING").description("Recipient email address for the synthetic test email").build(),
                                "subject", Schema.builder().type("STRING").description("Subject of the synthetic test email").build(),
                                "body", Schema.builder().type("STRING").description("Body content of the synthetic test email").build(),
                                "inReplyTo", Schema.builder().type("STRING").description("If set, simulates the email as a reply (Message-ID reference). Triggers 'reply' output on TRIGGER node.").build(),
                                "categoryIds", Schema.builder().type("ARRAY").items(Schema.builder().type("STRING").build())
                                        .description("Pre-assigned category UUIDs on the test email. Tests REMOVE_LABEL flows.").build(),
                                "assertions", Schema.builder().type("ARRAY").items(assertionSchema)
                                        .description("Assertions to verify per node. Use node IDs from the automation flow.").build()
                        ))
                        .required(List.of("automationId", "name", "from", "to", "subject", "body"))
                        .build())
                .build();
    }

    private FunctionDeclaration updateAutomationTest() {
        Schema assertionSchema = Schema.builder()
                .type("OBJECT")
                .properties(Map.of(
                        "nodeId", Schema.builder().type("STRING").description("UUID of the node to assert on").build(),
                        "expectedStatus", Schema.builder().type("STRING").description("Expected status: PASSED, MATCHED, NOT_MATCHED, EXTRACTED, CATEGORIZED, EXECUTED, SIMULATED, SKIPPED").build(),
                        "field", Schema.builder().type("STRING").description("Optional: specific field in resultDetail to check").build(),
                        "expectedValue", Schema.builder().type("STRING").description("Optional: expected value for that field").build()
                ))
                .required(List.of("nodeId", "expectedStatus"))
                .build();

        return FunctionDeclaration.builder()
                .name("update_automation_test")
                .description("Update an existing test case (assertions, email input, name). Use this instead of creating a new test when fixing assertion failures.")
                .parameters(Schema.builder()
                        .type("OBJECT")
                        .properties(Map.ofEntries(
                                Map.entry("automationId", Schema.builder().type("STRING").description("Automation UUID").build()),
                                Map.entry("testCaseId", Schema.builder().type("STRING").description("Test case UUID to update").build()),
                                Map.entry("name", Schema.builder().type("STRING").description("Updated test case name").build()),
                                Map.entry("description", Schema.builder().type("STRING").description("Updated description").build()),
                                Map.entry("from", Schema.builder().type("STRING").description("Sender email address").build()),
                                Map.entry("to", Schema.builder().type("STRING").description("Recipient email address").build()),
                                Map.entry("subject", Schema.builder().type("STRING").description("Subject").build()),
                                Map.entry("body", Schema.builder().type("STRING").description("Body content").build()),
                                Map.entry("inReplyTo", Schema.builder().type("STRING").description("Reply reference").build()),
                                Map.entry("categoryIds", Schema.builder().type("ARRAY").items(Schema.builder().type("STRING").build()).description("Category UUIDs").build()),
                                Map.entry("assertions", Schema.builder().type("ARRAY").items(assertionSchema).description("Updated assertions").build())
                        ))
                        .required(List.of("automationId", "testCaseId", "name", "from", "to", "subject", "body"))
                        .build())
                .build();
    }

    private FunctionDeclaration listAutomationTests() {
        return FunctionDeclaration.builder()
                .name("list_automation_tests")
                .description("List all test cases for an automation with their latest results")
                .parameters(Schema.builder()
                        .type("OBJECT")
                        .properties(Map.of(
                                "automationId", Schema.builder().type("STRING").description("Automation UUID").build()
                        ))
                        .required(List.of("automationId"))
                        .build())
                .build();
    }

    private FunctionDeclaration runAutomationTests() {
        return FunctionDeclaration.builder()
                .name("run_automation_tests")
                .description("Run ALL test cases for an automation in dry-run mode and return results with pass/fail status per test and per assertion")
                .parameters(Schema.builder()
                        .type("OBJECT")
                        .properties(Map.of(
                                "automationId", Schema.builder().type("STRING").description("Automation UUID").build()
                        ))
                        .required(List.of("automationId"))
                        .build())
                .build();
    }

    @SuppressWarnings("unchecked")
    private AutomationTestCaseRequest buildTestCaseRequest(Map<String, Object> args) {
        List<String> categoryIds = null;
        Object categoryIdsObj = args.get("categoryIds");
        if (categoryIdsObj instanceof List<?> list) {
            categoryIds = list.stream().map(Object::toString).toList();
        }

        TestEmailInput emailInput = new TestEmailInput(
                (String) args.get("from"),
                (String) args.get("to"),
                (String) args.get("subject"),
                (String) args.get("body"),
                (String) args.get("receivedAt"),
                (String) args.get("inReplyTo"),
                categoryIds,
                null,
                null
        , null);

        List<TestAssertion> assertions = new ArrayList<>();
        Object assertionsObj = args.get("assertions");
        if (assertionsObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    assertions.add(new TestAssertion(
                            UUID.fromString((String) map.get("nodeId")),
                            (String) map.get("expectedStatus"),
                            (String) map.get("field"),
                            (String) map.get("expectedValue")
                    ));
                }
            }
        }

        return new AutomationTestCaseRequest(
                (String) args.get("name"),
                (String) args.get("description"),
                emailInput,
                assertions,
                null
        );
    }
}
