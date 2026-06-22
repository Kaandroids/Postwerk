package com.postwerk.service.impl;

import com.postwerk.dto.*;
import com.postwerk.service.*;
import com.postwerk.util.UuidUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.Duration;

/**
 * Implementation of {@link WizardService} that manages wizard sessions in Redis
 * and orchestrates Gemini AI for the public automation wizard.
 */
@Service
public class WizardServiceImpl implements WizardService {

    private static final Logger log = LoggerFactory.getLogger(WizardServiceImpl.class);
    private static final String REDIS_KEY_PREFIX = "wizard:session:";
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);
    private static final int MAX_TURNS = 10;
    private static final int MAX_TOOL_ITERATIONS = 15;
    private static final int GEMINI_TIMEOUT_MS = 30_000;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentHashMap<UUID, AtomicBoolean> activeSessions = new ConcurrentHashMap<>();

    /**
     * Single reusable Gemini client for this service instance. The API key and httpOptions are
     * static, so a per-instance singleton is correct — building a new client on every wizard
     * stream call wastes connection pools and threads. Lazily initialized (double-checked locking)
     * and never closed: a long-lived {@link Client} is fine even though it is {@link AutoCloseable}.
     */
    private volatile Client client;

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.chat-model:gemini-2.5-flash}")
    private String chatModel;

    private final RedisTemplate<String, Object> redisTemplate;
    private final WizardToolRegistry wizardToolRegistry;
    private final CategoryService categoryService;
    private final ParameterSetService parameterSetService;
    private final TemplateService templateService;
    private final AutomationService automationService;
    private final ObjectMapper objectMapper;
    private final PromptService promptService;
    private final OrgContextService orgContextService;

    public WizardServiceImpl(RedisTemplate<String, Object> redisTemplate,
                             WizardToolRegistry wizardToolRegistry,
                             CategoryService categoryService,
                             ParameterSetService parameterSetService,
                             TemplateService templateService,
                             AutomationService automationService,
                             ObjectMapper objectMapper,
                             PromptService promptService,
                             OrgContextService orgContextService) {
        this.redisTemplate = redisTemplate;
        this.wizardToolRegistry = wizardToolRegistry;
        this.categoryService = categoryService;
        this.parameterSetService = parameterSetService;
        this.templateService = templateService;
        this.automationService = automationService;
        this.objectMapper = objectMapper;
        this.promptService = promptService;
        this.orgContextService = orgContextService;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns the shared Gemini client, building it on first use with an explicit per-request
     * timeout so a hung call cannot block indefinitely.
     */
    private Client client() {
        Client c = client;
        if (c == null) {
            synchronized (this) {
                c = client;
                if (c == null) {
                    c = Client.builder()
                            .apiKey(apiKey)
                            .httpOptions(HttpOptions.builder().timeout(GEMINI_TIMEOUT_MS).build())
                            .build();
                    client = c;
                }
            }
        }
        return c;
    }

    @Override
    public SseEmitter chatStream(WizardChatRequest request, String ipAddress) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key is not configured");
        }

        SseEmitter emitter = new SseEmitter(300_000L);

        executor.submit(() -> {
            UUID sessionId = null;
            AtomicBoolean cancelled = new AtomicBoolean(false);

            try {
                // Load or create session.
                //
                // IP-binding (denial-of-wallet guard): the wizard is public + unauthenticated and
                // drives Gemini unmetered. A caller could otherwise juggle/replay a stolen sessionId
                // from elsewhere to keep an existing turn budget alive or hijack another visitor's
                // session. So an existing session may only be continued from the SAME originating IP
                // it was created with; on a mismatch we transparently fall back to a fresh session
                // (which restarts the per-session MAX_TURNS budget and the 2000-char message cap).
                //
                // TODO: this is only a per-session guard. A platform-wide wizard token/cost budget
                //       should also be enforced centrally (e.g. a global rate limiter / shared cost
                //       cap for anonymous wizard traffic). Intentionally NOT modelled as a platform
                //       org row here to avoid FK risk; track separately.
                WizardSession session;
                if (request.sessionId() != null) {
                    session = loadSession(request.sessionId());
                    if (session == null) {
                        emitEvent(emitter, AiStreamEvent.error("Session expired or not found.", null));
                        emitter.complete();
                        return;
                    }
                    if (!ipMatches(session, ipAddress)) {
                        log.warn("Wizard: sessionId {} presented from a different IP than it was created with — "
                                + "ignoring supplied session and starting a fresh one", request.sessionId());
                        session = new WizardSession();
                        session.setLang(request.lang());
                        session.setIpAddress(ipAddress);
                    }
                } else {
                    session = new WizardSession();
                    session.setLang(request.lang());
                    session.setIpAddress(ipAddress);
                }

                sessionId = session.getId();
                activeSessions.put(sessionId, cancelled);

                // Enforce turn limit
                if (session.getTurnCount() >= MAX_TURNS) {
                    emitEvent(emitter, AiStreamEvent.error("Maximum conversation turns reached.", sessionId));
                    emitter.complete();
                    return;
                }

                session.setTurnCount(session.getTurnCount() + 1);

                // Add user message
                Map<String, Object> userMsg = new LinkedHashMap<>();
                userMsg.put("role", "user");
                userMsg.put("content", request.message());
                userMsg.put("timestamp", Instant.now().toString());
                session.getMessages().add(userMsg);

                // Determine tools based on phase
                String phase = session.getPhase();
                Tool tools;
                if ("building".equals(phase) || "ready".equals(phase)) {
                    tools = wizardToolRegistry.getBuildToolDeclarations();
                } else {
                    tools = wizardToolRegistry.getChatToolDeclarations();
                }

                String systemPrompt = buildWizardSystemPrompt(session.getLang(), phase);
                List<Content> contents = buildContents(session.getMessages());
                GenerateContentConfig config = GenerateContentConfig.builder()
                        .tools(List.of(tools))
                        .temperature(0.7f)
                        .maxOutputTokens(8192)
                        .systemInstruction(Content.builder()
                                .role("user")
                                .parts(List.of(Part.fromText(systemPrompt)))
                                .build())
                        .build();

                Client client = client();

                String assistantReply = null;

                for (int i = 0; i < MAX_TOOL_ITERATIONS; i++) {
                    if (cancelled.get()) {
                        emitEvent(emitter, AiStreamEvent.cancelled(sessionId));
                        break;
                    }

                    GenerateContentResponse response;
                    try {
                        response = client.models.generateContent(chatModel, contents, config);
                    } catch (RuntimeException e) {
                        log.error("Gemini API error in wizard stream: {}", e.getMessage());
                        emitEvent(emitter, AiStreamEvent.error("AI service error.", sessionId));
                        break;
                    }

                    List<FunctionCall> functionCalls;
                    try {
                        functionCalls = response.functionCalls();
                    } catch (IllegalArgumentException e) {
                        log.warn("Gemini bad finish reason in wizard: {}", e.getMessage());
                        if (i < MAX_TOOL_ITERATIONS - 1) {
                            contents.add(Content.builder().role("user")
                                    .parts(List.of(Part.fromText("Your previous response was malformed. Please try again.")))
                                    .build());
                            continue;
                        }
                        break;
                    }

                    if (functionCalls != null && !functionCalls.isEmpty()) {
                        List<Part> modelParts = response.parts();
                        if (modelParts == null || modelParts.isEmpty()) {
                            log.warn("Wizard: Gemini returned function calls but no parts");
                            break;
                        }
                        Content modelContent = Content.builder()
                                .role("model")
                                .parts(modelParts)
                                .build();
                        contents.add(modelContent);

                        List<Part> responseParts = new ArrayList<>();
                        for (FunctionCall fc : functionCalls) {
                            if (cancelled.get()) break;

                            String toolName = fc.name().orElse("");
                            Map<String, Object> args = fc.args().orElse(Map.of());

                            log.info("Wizard tool call: {}", toolName);
                            emitEvent(emitter, AiStreamEvent.toolStart(toolName, args, sessionId));

                            // Phase transition: propose_automation_plan → building
                            // Guard: require at least 2 user messages before allowing plan proposal
                            if ("propose_automation_plan".equals(toolName) && "chatting".equals(session.getPhase())) {
                                long userMsgCount = session.getMessages().stream()
                                        .filter(m -> "user".equals(m.get("role")))
                                        .count();
                                if (userMsgCount < 2) {
                                    log.info("Wizard: blocking propose_automation_plan — only {} user message(s)", userMsgCount);
                                    String errorMsg = "Not enough information yet. You must converse with the user first. Ask follow-up questions to understand their needs before proposing a plan. Minimum 2 user messages required.";
                                    emitEvent(emitter, AiStreamEvent.toolResult(toolName, errorMsg, false, sessionId));
                                    responseParts.add(Part.fromFunctionResponse(toolName,
                                            Map.of("error", errorMsg)));
                                    continue;
                                }
                                session.setPhase("building");
                                emitEvent(emitter, AiStreamEvent.phase("building", sessionId));
                                // Switch to build tools for next iteration
                                config = GenerateContentConfig.builder()
                                        .tools(List.of(wizardToolRegistry.getBuildToolDeclarations()))
                                        .temperature(0.7f)
                                        .maxOutputTokens(8192)
                                        .systemInstruction(Content.builder()
                                                .role("user")
                                                .parts(List.of(Part.fromText(buildWizardSystemPrompt(session.getLang(), "building"))))
                                                .build())
                                        .build();
                            }

                            Map<String, Object> toolResult = wizardToolRegistry.executeTool(toolName, args, session);
                            boolean success = Boolean.TRUE.equals(toolResult.get("success"));

                            emitEvent(emitter, AiStreamEvent.toolResult(toolName, toolResult.get("data"), success, sessionId));
                            responseParts.add(Part.fromFunctionResponse(toolName, toolResult));
                        }

                        if (cancelled.get()) break;

                        if (!responseParts.isEmpty()) {
                            contents.add(Content.builder()
                                    .role("user")
                                    .parts(responseParts)
                                    .build());
                        }
                    } else {
                        try {
                            assistantReply = response.text();
                        } catch (Exception textEx) {
                            log.warn("Wizard: failed to extract text from Gemini response: {}", textEx.getMessage());
                            assistantReply = null;
                        }
                        if (assistantReply != null && !assistantReply.isBlank()) {
                            emitEvent(emitter, AiStreamEvent.reply(assistantReply, sessionId));
                        }
                        break;
                    }
                }

                if (!cancelled.get()) {
                    if (assistantReply == null || assistantReply.isBlank()) {
                        // Choose fallback based on phase
                        assistantReply = "building".equals(session.getPhase()) || "ready".equals(session.getPhase())
                                ? "The automation has been built."
                                : "I'd love to help you set up an automation. Could you tell me more about what you'd like to automate?";
                        emitEvent(emitter, AiStreamEvent.reply(assistantReply, sessionId));
                    }

                    // Add assistant message to history
                    Map<String, Object> assistantMsg = new LinkedHashMap<>();
                    assistantMsg.put("role", "assistant");
                    assistantMsg.put("content", assistantReply);
                    assistantMsg.put("timestamp", Instant.now().toString());
                    session.getMessages().add(assistantMsg);

                    // If we have an automation plan with nodes, mark as ready
                    if (session.getAutomationPlan() != null && session.getAutomationPlan().containsKey("nodes")) {
                        session.setPhase("ready");
                        emitEvent(emitter, AiStreamEvent.phase("ready", sessionId));
                    }

                    saveSession(session);
                    emitEvent(emitter, AiStreamEvent.done(sessionId, session.getPhase()));
                }

                emitter.complete();
            } catch (Exception e) {
                log.error("Wizard SSE stream error for session {}: {}", sessionId, e.getMessage(), e);
                try {
                    emitEvent(emitter, AiStreamEvent.error("An error occurred.", sessionId));
                } catch (Exception emitEx) {
                    log.warn("Failed to emit wizard error: {}", emitEx.getMessage());
                }
                emitter.completeWithError(e);
            } finally {
                if (sessionId != null) {
                    activeSessions.remove(sessionId);
                }
            }
        });

        return emitter;
    }

    @Override
    public WizardClaimResponse claimSession(UUID sessionId, UUID userId, String ipAddress) {
        WizardSession session = loadSession(sessionId);
        if (session == null) {
            throw new IllegalStateException("Wizard session not found or expired");
        }
        if (!"ready".equals(session.getPhase())) {
            throw new IllegalStateException("Wizard session is not in ready phase");
        }
        if (session.getToolResults() == null || session.getToolResults().isEmpty()) {
            throw new IllegalStateException("Wizard session has no resources to claim");
        }
        Map<String, Object> plan = session.getAutomationPlan();
        if (plan == null || plan.isEmpty()) {
            throw new IllegalStateException("Wizard session has no automation plan");
        }

        // Claimed resources go into the claiming user's organization (#4) — their personal workspace
        // by default; collaborative orgs are selected later from the dashboard.
        UUID organizationId = orgContextService.resolve(userId, null).organizationId();

        // Map temp IDs to real IDs
        Map<String, UUID> idMapping = new LinkedHashMap<>();

        // Sort tool results by dependency order to avoid FK violations
        List<Map<String, Object>> sortedResults = sortByDependencyOrder(session.getToolResults());

        for (Map<String, Object> entry : sortedResults) {
            String tool = (String) entry.get("tool");
            @SuppressWarnings("unchecked")
            Map<String, Object> args = (Map<String, Object>) entry.get("args");
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) entry.get("result");

            String tempId = (String) result.get("id");

            try {
                switch (tool) {
                    case "create_category" -> {
                        CategoryRequest req = objectMapper.convertValue(args, CategoryRequest.class);
                        CategoryResponse created = categoryService.create(organizationId, userId, req, ipAddress);
                        if (tempId != null) idMapping.put(tempId, created.id());
                    }
                    case "create_parameter_set" -> {
                        // Remap parameterSetId references if needed
                        ParameterSetRequest req = objectMapper.convertValue(args, ParameterSetRequest.class);
                        ParameterSetResponse created = parameterSetService.create(organizationId, userId, req, ipAddress);
                        if (tempId != null) idMapping.put(tempId, created.id());
                    }
                    case "create_template" -> {
                        // Remap parameterSetId
                        Map<String, Object> remappedArgs = new LinkedHashMap<>(args);
                        remapId(remappedArgs, "parameterSetId", idMapping);
                        TemplateRequest req = objectMapper.convertValue(remappedArgs, TemplateRequest.class);
                        TemplateResponse created = templateService.create(organizationId, userId, req, ipAddress);
                        if (tempId != null) idMapping.put(tempId, created.id());
                    }
                    case "create_automation" -> {
                        AutomationRequest req = objectMapper.convertValue(args, AutomationRequest.class);
                        AutomationResponse created = automationService.create(organizationId, userId, req, ipAddress);
                        if (tempId != null) idMapping.put(tempId, created.id());
                    }
                    case "update_automation_flow" -> {
                        String automationTempId = (String) args.get("automationId");
                        UUID realAutomationId = idMapping.getOrDefault(automationTempId,
                                UuidUtil.parseOrNull(automationTempId));

                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> nodes = (List<Map<String, Object>>) args.get("nodes");
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> edges = (List<Map<String, Object>>) args.get("edges");

                        // Remap IDs in node configs
                        if (nodes != null) {
                            for (Map<String, Object> node : nodes) {
                                remapNodeConfig(node, idMapping);
                            }
                        }

                        Map<String, Object> flowArgs = new LinkedHashMap<>();
                        flowArgs.put("automationId", realAutomationId.toString());
                        flowArgs.put("nodes", nodes);
                        flowArgs.put("edges", edges);

                        FlowUpdateRequest flowReq = buildFlowUpdateRequest(flowArgs);
                        automationService.updateFlow(organizationId, userId, realAutomationId, flowReq, ipAddress);
                    }
                    default -> log.warn("Unknown wizard tool during claim: {}", tool);
                }
            } catch (Exception e) {
                log.error("Failed to claim wizard tool result {}: {}", tool, e.getMessage(), e);
                throw new RuntimeException("Failed to create " + tool + ": " + e.getMessage(), e);
            }
        }

        // Find the automation ID
        UUID automationId = null;
        for (Map<String, Object> entry : session.getToolResults()) {
            if ("create_automation".equals(entry.get("tool"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) entry.get("result");
                String tempId = (String) result.get("id");
                automationId = idMapping.get(tempId);
                break;
            }
        }

        // Delete session
        deleteSession(sessionId);

        if (automationId == null) {
            throw new IllegalStateException("No automation was created in wizard session");
        }

        return new WizardClaimResponse(automationId);
    }

    @Override
    public WizardSessionResponse getSession(UUID sessionId) {
        WizardSession session = loadSession(sessionId);
        if (session == null) {
            return null;
        }
        return new WizardSessionResponse(session.getId(), session.getPhase(),
                session.getMessages(), session.getAutomationPlan());
    }

    // ─── Redis operations ───────────────────────────────────────────────

    private WizardSession loadSession(UUID sessionId) {
        String key = REDIS_KEY_PREFIX + sessionId;
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) return null;
        // Extend TTL on access
        redisTemplate.expire(key, SESSION_TTL);
        return objectMapper.convertValue(value, WizardSession.class);
    }

    private void saveSession(WizardSession session) {
        String key = REDIS_KEY_PREFIX + session.getId();
        redisTemplate.opsForValue().set(key, session, SESSION_TTL);
    }

    private void deleteSession(UUID sessionId) {
        redisTemplate.delete(REDIS_KEY_PREFIX + sessionId);
    }

    // ─── System prompt ──────────────────────────────────────────────────

    private String buildWizardSystemPrompt(String lang, String phase) {
        if ("chatting".equals(phase)) {
            String template = "de".equals(lang) ? "wizard-chat-de.txt" : "wizard-chat-en.txt";
            return promptService.load(template);
        } else {
            String langLabel = "de".equals(lang) ? "German" : "English";
            // Shared node-config reference (same source the post-login assistant uses) so the
            // wizard builds nodes with identical config rules (FILTER DNF, sourceVariables, etc.).
            return promptService.load("wizard-build.txt", Map.of("lang", langLabel))
                    + "\n\n" + promptService.load("node-config-reference.txt");
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private List<Content> buildContents(List<Map<String, Object>> messages) {
        List<Content> contents = new ArrayList<>();
        for (Map<String, Object> msg : messages) {
            String role = (String) msg.get("role");
            String content = (String) msg.get("content");
            if (content != null && !content.isBlank()) {
                String geminiRole = "assistant".equals(role) ? "model" : "user";
                contents.add(Content.builder()
                        .role(geminiRole)
                        .parts(List.of(Part.fromText(content)))
                        .build());
            }
        }
        return contents;
    }

    private void emitEvent(SseEmitter emitter, AiStreamEvent event) {
        try {
            emitter.send(SseEmitter.event().data(event));
        } catch (IOException e) {
            log.warn("Failed to send wizard SSE event: {}", e.getMessage());
        }
    }

    /**
     * Returns {@code true} when the request's originating IP matches the IP the session was created
     * with, binding a wizard session to a single client (denial-of-wallet guard). A session created
     * before IP capture (no stored IP) is treated as bound to the first IP that continues it.
     */
    private boolean ipMatches(WizardSession session, String ipAddress) {
        String stored = session.getIpAddress();
        if (stored == null || stored.isBlank()) {
            // Legacy/missing IP — bind to the current caller rather than rejecting outright.
            session.setIpAddress(ipAddress);
            return true;
        }
        return stored.equals(ipAddress);
    }

    @SuppressWarnings("unchecked")
    private void remapNodeConfig(Map<String, Object> node, Map<String, UUID> idMapping) {
        String configStr = (String) node.get("config");
        if (configStr == null) return;

        try {
            Map<String, Object> config = objectMapper.readValue(configStr, new TypeReference<>() {});

            // Remap templateId
            remapId(config, "templateId", idMapping);

            // Remap categoryIds
            if (config.containsKey("categoryIds")) {
                List<String> ids = (List<String>) config.get("categoryIds");
                if (ids != null) {
                    config.put("categoryIds", ids.stream()
                            .map(id -> {
                                UUID real = idMapping.get(id);
                                return real != null ? real.toString() : id;
                            })
                            .toList());
                }
            }

            // Remap extractions parameterSetId
            if (config.containsKey("extractions")) {
                List<Map<String, Object>> extractions = (List<Map<String, Object>>) config.get("extractions");
                if (extractions != null) {
                    for (Map<String, Object> ext : extractions) {
                        remapId(ext, "parameterSetId", idMapping);
                    }
                }
            }

            // Remap checks categoryIds
            if (config.containsKey("checks")) {
                List<Map<String, Object>> checks = (List<Map<String, Object>>) config.get("checks");
                if (checks != null) {
                    for (Map<String, Object> check : checks) {
                        if (check.containsKey("categoryIds")) {
                            List<String> cids = (List<String>) check.get("categoryIds");
                            if (cids != null) {
                                check.put("categoryIds", cids.stream()
                                        .map(id -> {
                                            UUID real = idMapping.get(id);
                                            return real != null ? real.toString() : id;
                                        })
                                        .toList());
                            }
                        }
                    }
                }
            }

            // Clear placeholder accountIds — user will configure after registration
            if (config.containsKey("accountIds")) {
                config.put("accountIds", List.of());
            }

            node.put("config", objectMapper.writeValueAsString(config));
        } catch (Exception e) {
            log.warn("Failed to remap node config: {}", e.getMessage());
        }
    }

    private void remapId(Map<String, Object> map, String field, Map<String, UUID> idMapping) {
        Object val = map.get(field);
        if (val instanceof String strVal) {
            UUID real = idMapping.get(strVal);
            if (real != null) {
                map.put(field, real.toString());
            }
        }
    }

    /**
     * Sorts wizard tool results by dependency order so that resources are created
     * before the resources that reference them (e.g. parameter_sets before templates).
     */
    private List<Map<String, Object>> sortByDependencyOrder(List<Map<String, Object>> toolResults) {
        Map<String, Integer> order = Map.of(
                "create_category", 0,
                "create_parameter_set", 1,
                "create_template", 2,
                "create_automation", 3,
                "update_automation_flow", 4
        );
        List<Map<String, Object>> sorted = new ArrayList<>(toolResults);
        sorted.sort(Comparator.comparingInt(entry -> order.getOrDefault(entry.get("tool"), 99)));
        return sorted;
    }

    private FlowUpdateRequest buildFlowUpdateRequest(Map<String, Object> args) {
        Object nodesObj = args.get("nodes");
        Object edgesObj = args.get("edges");

        List<FlowUpdateRequest.FlowNodeRequest> nodes = objectMapper.convertValue(
                nodesObj, new TypeReference<>() {});
        List<FlowUpdateRequest.FlowEdgeRequest> edges = objectMapper.convertValue(
                edgesObj, new TypeReference<>() {});

        return new FlowUpdateRequest(nodes, edges, null);
    }
}
