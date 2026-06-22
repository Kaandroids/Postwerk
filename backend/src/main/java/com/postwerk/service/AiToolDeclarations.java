package com.postwerk.service;

import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;

import java.util.List;
import java.util.Map;

/**
 * Single source of truth for the <b>resource-building</b> AI tool declarations shared by the
 * post-login assistant ({@link AiToolRegistry}) and the public onboarding wizard
 * ({@link WizardToolRegistry}).
 *
 * <p>These describe <em>what</em> a category / template / parameter set / automation flow looks
 * like — identical concepts regardless of which surface drives them — so their schemas and
 * descriptions must not drift apart. The authoritative wording is the assistant's.</p>
 *
 * <p><b>Not included:</b> {@code propose_automation_plan}. That tool is control-flow, and its
 * description is tied to each surface's distinct phase machine (assistant: OPEN→PLANNING→BUILDING
 * with an explicit user confirmation; wizard: chatting→building with an immediate build). Each
 * registry therefore keeps its own copy.</p>
 *
 * <p><b>Note:</b> these declarations only describe the schema the model sees. Execution stays
 * per-registry — the assistant persists straight to the DB (user-scoped), while the wizard stages
 * temporary, session-bound copies until the account is claimed.</p>
 *
 * @since 1.0
 */
public final class AiToolDeclarations {

    private AiToolDeclarations() {
    }

    public static FunctionDeclaration createCategory() {
        return FunctionDeclaration.builder()
                .name("create_category")
                .description("Create a new email category for AI classification")
                .parameters(Schema.builder()
                        .type("OBJECT")
                        .properties(Map.of(
                                "name", Schema.builder().type("STRING").description("Category name (min 3 chars)").build(),
                                "color", Schema.builder().type("STRING").description("Hex color code, e.g. #3B82F6").build(),
                                "description", Schema.builder().type("STRING").description("Detailed description of the category (min 30 chars)").build(),
                                "positiveExample", Schema.builder().type("STRING").description("Example email that belongs to this category").build(),
                                "negativeExample", Schema.builder().type("STRING").description("Example email that does NOT belong to this category").build()
                        ))
                        .required(List.of("name", "color", "description"))
                        .build())
                .build();
    }

    public static FunctionDeclaration createTemplate() {
        return FunctionDeclaration.builder()
                .name("create_template")
                .description("Create a new email reply template. Body supports {{placeholder}} variables.")
                .parameters(Schema.builder()
                        .type("OBJECT")
                        .properties(Map.of(
                                "name", Schema.builder().type("STRING").description("Template name").build(),
                                "subject", Schema.builder().type("STRING").description("Email subject line").build(),
                                "body", Schema.builder().type("STRING").description(
                                        "Email body as rich HTML. MUST use proper HTML tags for professional formatting: "
                                        + "<h2> for headings, <p> for paragraphs, <strong>/<em> for emphasis, "
                                        + "<ul>/<li> for lists, <table>/<tr>/<td> for tabular data, <br> for line breaks, "
                                        + "<a href=\"...\"> for links. Use inline CSS for styling (e.g. style=\"color:#333; font-family:Arial,sans-serif;\"). "
                                        + "Supports {{placeholder}} variables: {{fromName}}, {{fromAddress}}, {{subject}}, {{toAddress}}, {{receivedAt}}, "
                                        + "and extraction variables using EXACT parameter names from the linked parameter set (e.g. {{invoiceNumber}}, {{customerName}}). "
                                        + "IMPORTANT: Use {{fieldName}} syntax — NOT {{extraction_0_fieldName}}! The field names must exactly match the parameter set's parameter names. "
                                        + "Example: <div style=\"font-family:Arial,sans-serif;\"><h2>Thank you, {{fromName}}!</h2>"
                                        + "<p>We received your request regarding <strong>{{subject}}</strong>.</p></div>"
                                ).build(),
                                "parameterSetId", Schema.builder().type("STRING").description(
                                        "Parameter set UUID to link to this template. REQUIRED when the template uses extraction "
                                        + "placeholders ({{fieldName}}). First create a parameter set with create_parameter_set, "
                                        + "then pass its UUID here. The placeholder names in the template body MUST match the parameter names in the set."
                                ).build()
                        ))
                        .required(List.of("name", "subject", "body"))
                        .build())
                .build();
    }

    public static FunctionDeclaration createParameterSet() {
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
                .name("create_parameter_set")
                .description("Create a parameter set (extraction schema) for structured data extraction from emails")
                .parameters(Schema.builder()
                        .type("OBJECT")
                        .properties(Map.of(
                                "name", Schema.builder().type("STRING").description("Parameter set name (min 3 chars)").build(),
                                "parameters", Schema.builder().type("ARRAY").items(paramSchema).description("List of parameters to extract").build()
                        ))
                        .required(List.of("name", "parameters"))
                        .build())
                .build();
    }

    public static FunctionDeclaration createAutomation() {
        return FunctionDeclaration.builder()
                .name("create_automation")
                .description("Create a new automation (starts as PAUSED). Use update_automation_flow to add nodes/edges.")
                .parameters(Schema.builder()
                        .type("OBJECT")
                        .properties(Map.of(
                                "name", Schema.builder().type("STRING").description("Automation name").build(),
                                "description", Schema.builder().type("STRING").description("What this automation does").build(),
                                "color", Schema.builder().type("STRING").description("Hex color code").build()
                        ))
                        .required(List.of("name"))
                        .build())
                .build();
    }

    public static FunctionDeclaration updateAutomationFlow() {
        Schema nodeSchema = Schema.builder()
                .type("OBJECT")
                .properties(Map.of(
                        "id", Schema.builder().type("STRING").description("Temporary node ID (e.g. node_0, node_1)").build(),
                        "nodeType", Schema.builder().type("STRING").description("Node type. One of: TRIGGER, FILTER, EXTRACT, CATEGORIZE, DELAY, LABEL, EMAIL_ACTION, REMOVE_LABEL, WEBHOOK, SEND_EMAIL. Determines the required config format.").build(),
                        "label", Schema.builder().type("STRING").description("Display label").build(),
                        "positionX", Schema.builder().type("NUMBER").description("X position on canvas").build(),
                        "positionY", Schema.builder().type("NUMBER").description("Y position on canvas").build(),
                        "config", Schema.builder().type("STRING").description(
                                "JSON config string. MUST match the nodeType exactly:\n"
                                + "TRIGGER: {\"triggerMode\":\"EMAIL\",\"accountIds\":[\"<email-account-uuid>\"]} — modes: EMAIL (accountIds), WEBHOOK (inbound receiver; optional parameterSetId to map request fields), CRON (scheduleType,intervalMinutes,cronExpression,preset)\n"
                                + "FILTER: {\"checks\":[{\"label\":\"Check name\",\"groups\":[{\"conditions\":[{\"field\":\"email.from\",\"operator\":\"CONTAINS\",\"value\":\"example.com\"}]}]}]} — multi-check variable-based conditions\n"
                                + "EXTRACT: {\"extractions\":[{\"parameterSetId\":\"<UUID>\",\"label\":\"Name\"}]}\n"
                                + "CATEGORIZE: {\"categoryIds\":[\"<UUID1>\",\"<UUID2>\"],\"threshold\":70}\n"
                                + "DELAY: {\"delayMinutes\":30}\n"
                                + "LABEL: {\"categoryId\":\"<UUID>\"}\n"
                                + "EMAIL_ACTION: {\"actionMode\":\"REPLY\",\"contentSource\":\"VORLAGE\",\"templateId\":\"<UUID>\"} — actionMode: REPLY (contentSource VORLAGE+templateId or MANUAL+subject/body), FORWARD (toAddress required), MOVE_FOLDER (folder required, use \"__TRASH__\" for trash)\n"
                                + "REMOVE_LABEL: {\"categoryId\":\"<UUID>\"}\n"
                                + "SEND_EMAIL: {\"senderAccountId\":\"<UUID>\",\"to\":\"{{email.from}}\",\"contentSource\":\"MANUAL\",\"subject\":\"...\",\"body\":\"<p>...</p>\"} — sends a NEW email (works without an email trigger)\n"
                                + "WEBHOOK: {\"url\":\"https://api.example.com/endpoint\",\"method\":\"POST\",\"headers\":[{\"key\":\"Content-Type\",\"value\":\"application/json\"}],\"body\":\"{\\\"text\\\":\\\"{{email.subject}}\\\"}\",\"authType\":\"NONE\",\"timeout\":30} — outbound call, routes to success/failure"
                        ).build()
                ))
                .required(List.of("id", "nodeType", "label", "positionX", "positionY", "config"))
                .build();

        Schema edgeSchema = Schema.builder()
                .type("OBJECT")
                .properties(Map.of(
                        "id", Schema.builder().type("STRING").description("Temporary edge ID").build(),
                        "sourceNodeId", Schema.builder().type("STRING").description("Source node temp ID").build(),
                        "sourceHandle", Schema.builder().type("STRING").description("Source handle: TRIGGER(EMAIL)='new-email'/'reply', TRIGGER(WEBHOOK/CRON)='output', FILTER='check_0'/'check_1'/.../'fallback', EXTRACT='extraction_0'/..., CATEGORIZE='category_0'/.../'uncategorized', WEBHOOK='resp_0'/'resp_1'/.../'unmatched' (one per responseSchemas entry, by index, + the always-present 'unmatched' catch-all), EMAIL_ACTION='output', all others='output'").build(),
                        "targetNodeId", Schema.builder().type("STRING").description("Target node temp ID").build(),
                        "targetHandle", Schema.builder().type("STRING").description("Target handle name (input)").build()
                ))
                .required(List.of("id", "sourceNodeId", "sourceHandle", "targetNodeId", "targetHandle"))
                .build();

        return FunctionDeclaration.builder()
                .name("update_automation_flow")
                .description("Set the nodes and edges of an automation flow. Replaces any existing flow. "
                        + "CRITICAL: Before calling this, you must have already created all dependencies (templates, parameter sets, categories) "
                        + "using their respective create tools, and you must use the returned UUIDs in node configs. "
                        + "FILTER conditions are defined inline via 'checks' (no external dependency). "
                        + "For EMAIL_ACTION in REPLY/FORWARD with contentSource=VORLAGE, templateId is MANDATORY.")
                .parameters(Schema.builder()
                        .type("OBJECT")
                        .properties(Map.of(
                                "automationId", Schema.builder().type("STRING").description("Automation UUID").build(),
                                "nodes", Schema.builder().type("ARRAY").items(nodeSchema).description("Flow nodes").build(),
                                "edges", Schema.builder().type("ARRAY").items(edgeSchema).description("Flow edges connecting nodes").build()
                        ))
                        .required(List.of("automationId", "nodes", "edges"))
                        .build())
                .build();
    }
}
