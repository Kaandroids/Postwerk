package com.postwerk.service.impl;

import com.postwerk.dto.*;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.AiConversation;
import com.postwerk.model.AuditAction;
import com.postwerk.model.ConversationPhase;
import com.postwerk.repository.AiConversationRepository;
import com.postwerk.service.*;
import com.google.genai.Client;
import com.google.genai.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

/**
 * Default implementation of {@link AiAssistantService}.
 *
 * <p>Orchestrates conversational AI interactions by managing conversation persistence,
 * building dynamic system prompts with user resource context, and executing an iterative
 * tool-call loop against the Gemini chat model. Supports CRUD operations on conversations
 * and integrates with audit logging for compliance tracking.</p>
 *
 * @since 1.0
 */
@Service
public class AiAssistantServiceImpl implements AiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantServiceImpl.class);
    private static final int MAX_TOOL_ITERATIONS = 20;
    private static final int MAX_TEST_RUN_CALLS = 8;

    private static final int MAX_MESSAGE_LENGTH = 10_000;
    private static final int MAX_CONCURRENT_STREAMS_PER_USER = 3;
    private static final int GEMINI_TIMEOUT_MS = 30_000;
    private static final Set<String> ALLOWED_MODELS = Set.of("gemini-2.5-flash", "gemini-2.5-pro");

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentHashMap<UUID, AtomicBoolean> activeSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, java.util.concurrent.atomic.AtomicInteger> userStreamCounts = new ConcurrentHashMap<>();

    /**
     * Single reusable Gemini client for this service instance. The API key and httpOptions are
     * static, so a per-instance singleton is correct — building a new client on every chat/stream
     * call wastes connection pools and threads. Lazily initialized (double-checked locking) and
     * never closed: a long-lived {@link Client} is fine even though it is {@link AutoCloseable}.
     */
    private volatile Client client;

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.chat-model:gemini-2.5-flash}")
    private String chatModel;

    @Value("${gemini.chat-temperature:0.7}")
    private float chatTemperature;

    @Value("${gemini.chat-max-tokens:8192}")
    private int chatMaxTokens;

    private final QuotaService quotaService;
    private final AiConversationRepository conversationRepository;
    private final AiToolRegistry toolRegistry;
    private final AiUsageService aiUsageService;
    private final AuditService auditService;
    private final ConversationMessageCodec messageCodec;
    private final ConversationPhaseManager phaseManager;
    private final SystemPromptBuilder systemPromptBuilder;

    public AiAssistantServiceImpl(QuotaService quotaService,
                                  AiConversationRepository conversationRepository,
                                  AiToolRegistry toolRegistry,
                                  AiUsageService aiUsageService,
                                  AuditService auditService,
                                  ConversationMessageCodec messageCodec,
                                  ConversationPhaseManager phaseManager,
                                  SystemPromptBuilder systemPromptBuilder) {
        this.quotaService = quotaService;
        this.conversationRepository = conversationRepository;
        this.toolRegistry = toolRegistry;
        this.aiUsageService = aiUsageService;
        this.auditService = auditService;
        this.messageCodec = messageCodec;
        this.phaseManager = phaseManager;
        this.systemPromptBuilder = systemPromptBuilder;
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

    private void validateMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message cannot be empty");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Message exceeds maximum length of " + MAX_MESSAGE_LENGTH + " characters");
        }
    }

    private String resolveModel(AiChatRequest request) {
        return request.model() != null && ALLOWED_MODELS.contains(request.model())
                ? request.model()
                : chatModel;
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
    public AiChatResponse chat(UUID organizationId, UUID userId, AiChatRequest request, String ipAddress) {
        validateMessage(request.message());
        quotaService.checkAiTokenQuota(organizationId);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key is not configured");
        }

        String model = resolveModel(request);

        // Load or create conversation
        AiConversation conversation;
        List<Map<String, Object>> messageHistory;

        if (request.conversationId() != null) {
            conversation = conversationRepository.findByIdAndUserId(request.conversationId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Conversation", request.conversationId()));
            messageHistory = messageCodec.deserialize(conversation.getMessages());
        } else {
            conversation = AiConversation.builder()
                    .userId(userId)
                    .organizationId(organizationId)
                    .messages("[]")
                    .build();
            conversation = conversationRepository.save(conversation);
            messageHistory = new ArrayList<>();
        }

        // Add user message to history
        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", request.message());
        userMsg.put("timestamp", Instant.now().toString());
        messageHistory.add(userMsg);

        // Phase transition: check user confirmation/cancellation before calling Gemini
        phaseManager.handleUserMessage(conversation, request.message(), messageHistory);

        // Build system prompt with dynamic context (phase-aware)
        String systemPrompt = systemPromptBuilder.build(userId, organizationId, conversation.getPhase(), request.language());

        // Trim old messages to prevent context overflow (keep last N)
        List<Map<String, Object>> trimmedHistory = messageCodec.trim(messageHistory);

        // Build Gemini contents from history
        List<Content> contents = messageCodec.buildContents(trimmedHistory);

        // Configure tools based on conversation phase
        ConversationPhase currentPhase = conversation.getPhase();
        GenerateContentConfig config = GenerateContentConfig.builder()
                .tools(List.of(toolRegistry.getToolDeclarations(currentPhase)))
                .temperature(chatTemperature)
                .maxOutputTokens(chatMaxTokens)
                // Exclude internal "thinking" parts from the response so they (and any tool_code the
                // model writes while reasoning) never leak into the assistant's text reply. The model
                // still emits proper functionCall parts, so plan/tool detection keeps working.
                .thinkingConfig(ThinkingConfig.builder().includeThoughts(false).build())
                .systemInstruction(Content.builder()
                        .role("user")
                        .parts(List.of(Part.fromText(systemPrompt)))
                        .build())
                .build();

        Client client = client();

        // Tool call loop
        List<AiToolCallDto> allToolCalls = new ArrayList<>();
        String assistantReply = null;

        for (int i = 0; i < MAX_TOOL_ITERATIONS; i++) {
            GenerateContentResponse response;
            try {
                response = generateWithRetry(client, model, contents, config);
            } catch (RuntimeException e) {
                log.error("Gemini API error: {}", e.getMessage());
                assistantReply = "The AI service encountered an error. Please try again.";
                break;
            }
            aiUsageService.recordGenerateContent(organizationId, userId, model, "AI_CHAT", response);

            List<FunctionCall> functionCalls;
            try {
                functionCalls = response.functionCalls();
            } catch (IllegalArgumentException e) {
                log.warn("Gemini bad finish reason on iteration {}: {}", i, e.getMessage());
                if (i < MAX_TOOL_ITERATIONS - 1) {
                    contents.add(Content.builder().role("user")
                            .parts(List.of(Part.fromText("Your previous response was malformed. Please try again with a valid function call or a text response.")))
                            .build());
                    continue;
                }
                assistantReply = "Sorry, the AI could not complete the request. Please rephrase and try again.";
                break;
            }

            if (functionCalls != null && !functionCalls.isEmpty()) {
                // Model wants to call tools
                Content modelContent = Content.builder()
                        .role("model")
                        .parts(response.parts())
                        .build();
                contents.add(modelContent);

                List<Part> responseParts = new ArrayList<>();
                for (FunctionCall fc : functionCalls) {
                    String toolName = fc.name().orElse("");
                    Map<String, Object> args = fc.args().orElse(Map.of());

                    log.info("AI tool call: {}", toolName);
                    log.debug("AI tool call args for {}: {}", toolName, args);

                    // The model invoking a build tool IS the confirmation signal — trust its NLU of the
                    // user's intent over the brittle confirmation regex. Auto-promote PLANNING → BUILDING
                    // so the build runs instead of being blocked (which otherwise made the model apologize).
                    if (currentPhase == ConversationPhase.PLANNING && toolRegistry.isAutomationWriteTool(toolName)) {
                        conversation.setPhase(ConversationPhase.BUILDING);
                        currentPhase = ConversationPhase.BUILDING;
                        log.info("Phase transition: PLANNING → BUILDING (model invoked build tool {}, conversation {})",
                                toolName, conversation.getId());
                    }

                    Map<String, Object> toolResult = toolRegistry.executeTool(toolName, args, organizationId, userId, ipAddress, currentPhase);
                    boolean success = Boolean.TRUE.equals(toolResult.get("success"));

                    // Handle phase transitions from tool calls — AFTER the tool runs, so the
                    // propose_automation_plan call itself executes while still OPEN (the tool guard
                    // rejects it once the phase is PLANNING). Only transition on a successful proposal.
                    if (success && "propose_automation_plan".equals(toolName) && conversation.getPhase() == ConversationPhase.OPEN) {
                        conversation.setPhase(ConversationPhase.PLANNING);
                        currentPhase = ConversationPhase.PLANNING;
                        log.info("Phase transition: OPEN → PLANNING (conversation {})", conversation.getId());
                    }

                    allToolCalls.add(new AiToolCallDto(toolName, args, toolResult.get("data"), success,
                            toolResult.get("validationIssues")));

                    responseParts.add(Part.fromFunctionResponse(toolName, toolResult));
                }

                Content toolContent = Content.builder()
                        .role("user")
                        .parts(responseParts)
                        .build();
                contents.add(toolContent);
            } else {
                // Model returned text
                assistantReply = response.text();
                break;
            }
        }

        // Reset BUILDING → OPEN at end of turn (after all tool calls + final response)
        if (conversation.getPhase() == ConversationPhase.BUILDING) {
            conversation.setPhase(ConversationPhase.OPEN);
            log.info("Phase transition: BUILDING → OPEN (turn completed, conversation {})", conversation.getId());
        }

        if (assistantReply == null) {
            assistantReply = defaultReply(!allToolCalls.isEmpty(), request.message(), request.language());
        }

        // Add assistant message to history
        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", assistantReply);
        assistantMsg.put("timestamp", Instant.now().toString());
        if (!allToolCalls.isEmpty()) {
            assistantMsg.put("toolCalls", allToolCalls);
        }
        messageHistory.add(assistantMsg);

        // Auto-generate title on first message (strip HTML to prevent stored XSS)
        if (conversation.getTitle() == null || conversation.getTitle().isBlank()) {
            String sanitized = request.message().replaceAll("<[^>]*>", "").trim();
            String title = sanitized.length() > 50
                    ? sanitized.substring(0, 50) + "…"
                    : sanitized;
            conversation.setTitle(title);
        }

        // Save conversation
        conversation.setMessages(messageCodec.serialize(messageHistory));
        conversationRepository.save(conversation);

        auditService.log(userId, AuditAction.AI_CHAT,
                "AI chat in conversation " + conversation.getId(), ipAddress);

        return new AiChatResponse(conversation.getId(), assistantReply, allToolCalls,
                conversation.getPhase().name());
    }

    @Override
    public SseEmitter chatStream(UUID organizationId, UUID userId, AiChatRequest request, String ipAddress) {
        validateMessage(request.message());
        quotaService.checkAiTokenQuota(organizationId);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key is not configured");
        }

        // Limit concurrent streams per user
        var streamCount = userStreamCounts.computeIfAbsent(userId, k -> new java.util.concurrent.atomic.AtomicInteger(0));
        if (streamCount.incrementAndGet() > MAX_CONCURRENT_STREAMS_PER_USER) {
            streamCount.decrementAndGet();
            throw new IllegalStateException("Too many concurrent chat streams. Please wait for the current request to finish.");
        }

        String model = resolveModel(request);
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        executor.submit(() -> {
            UUID conversationId = null;
            AtomicBoolean cancelled = new AtomicBoolean(false);

            try {
                // Load or create conversation
                AiConversation conversation;
                List<Map<String, Object>> messageHistory;

                if (request.conversationId() != null) {
                    conversation = conversationRepository.findByIdAndUserId(request.conversationId(), userId)
                            .orElseThrow(() -> new ResourceNotFoundException("Conversation", request.conversationId()));
                    messageHistory = messageCodec.deserialize(conversation.getMessages());
                } else {
                    conversation = AiConversation.builder()
                            .userId(userId)
                            .organizationId(organizationId)
                            .messages("[]")
                            .build();
                    conversation = conversationRepository.save(conversation);
                    messageHistory = new ArrayList<>();
                }

                conversationId = conversation.getId();
                activeSessions.put(conversationId, cancelled);

                // Add user message to history
                Map<String, Object> userMsg = new LinkedHashMap<>();
                userMsg.put("role", "user");
                userMsg.put("content", request.message());
                userMsg.put("timestamp", Instant.now().toString());
                messageHistory.add(userMsg);

                // Phase transition: check user confirmation/cancellation
                ConversationPhase phaseBefore = conversation.getPhase();
                phaseManager.handleUserMessage(conversation, request.message(), messageHistory);
                ConversationPhase currentPhase = conversation.getPhase();
                if (currentPhase != phaseBefore) {
                    conversationRepository.save(conversation);
                    emitEvent(emitter, AiStreamEvent.phase(currentPhase.name(), conversationId));
                }

                String systemPrompt = systemPromptBuilder.build(userId, organizationId, currentPhase, request.language());
                List<Map<String, Object>> trimmedHistory = messageCodec.trim(messageHistory);
                List<Content> contents = messageCodec.buildContents(trimmedHistory);
                GenerateContentConfig config = GenerateContentConfig.builder()
                        .tools(List.of(toolRegistry.getToolDeclarations(currentPhase)))
                        .temperature(chatTemperature)
                        .maxOutputTokens(chatMaxTokens)
                        // Exclude internal "thinking" parts (and any tool_code) from the streamed reply.
                        .thinkingConfig(ThinkingConfig.builder().includeThoughts(false).build())
                        .systemInstruction(Content.builder()
                                .role("user")
                                .parts(List.of(Part.fromText(systemPrompt)))
                                .build())
                        .build();

                Client client = client();

                List<AiToolCallDto> allToolCalls = new ArrayList<>();
                String assistantReply = null;
                int testRunCount = 0;

                for (int i = 0; i < MAX_TOOL_ITERATIONS; i++) {
                    if (cancelled.get()) {
                        emitEvent(emitter, AiStreamEvent.cancelled(conversationId));
                        break;
                    }

                    GenerateContentResponse response;
                    try {
                        response = generateWithRetry(client, model, contents, config);
                    } catch (RuntimeException e) {
                        log.error("Gemini API error in stream: {}", e.getMessage());
                        emitEvent(emitter, AiStreamEvent.error("The AI service encountered an error.", conversationId));
                        assistantReply = "The AI service encountered an error. Please try again.";
                        break;
                    }
                    aiUsageService.recordGenerateContent(organizationId, userId, model, "AI_CHAT", response);

                    List<FunctionCall> functionCalls;
                    try {
                        functionCalls = response.functionCalls();
                    } catch (IllegalArgumentException e) {
                        log.warn("Gemini bad finish reason on stream iteration {}: {}", i, e.getMessage());
                        if (i < MAX_TOOL_ITERATIONS - 1) {
                            contents.add(Content.builder().role("user")
                                    .parts(List.of(Part.fromText("Your previous response was malformed. Please try again with a valid function call or a text response.")))
                                    .build());
                            continue;
                        }
                        assistantReply = "Sorry, the AI could not complete the request. Please rephrase and try again.";
                        break;
                    }

                    if (functionCalls != null && !functionCalls.isEmpty()) {
                        Content modelContent = Content.builder()
                                .role("model")
                                .parts(response.parts())
                                .build();
                        contents.add(modelContent);

                        List<Part> responseParts = new ArrayList<>();
                        for (FunctionCall fc : functionCalls) {
                            if (cancelled.get()) break;

                            String toolName = fc.name().orElse("");
                            Map<String, Object> args = fc.args().orElse(Map.of());

                            // Enforce test run limit
                            if ("run_automation_tests".equals(toolName)) {
                                testRunCount++;
                                if (testRunCount > MAX_TEST_RUN_CALLS) {
                                    Map<String, Object> limitResult = Map.of("success", false, "error",
                                            "Maximum test re-runs exceeded. Analyze remaining failures, explain what you tried, and ask the user for guidance.");
                                    allToolCalls.add(new AiToolCallDto(toolName, args, null, false));
                                    emitEvent(emitter, AiStreamEvent.toolResult(toolName, null, false, conversationId));
                                    responseParts.add(Part.fromFunctionResponse(toolName, limitResult));
                                    continue;
                                }
                            }

                            log.info("AI stream tool call: {}", toolName);
                            log.debug("AI stream tool call args for {}: {}", toolName, args);
                            emitEvent(emitter, AiStreamEvent.toolStart(toolName, args, conversationId));

                            // The model invoking a build tool IS the confirmation signal — auto-promote
                            // PLANNING → BUILDING so the build runs instead of being blocked.
                            if (currentPhase == ConversationPhase.PLANNING && toolRegistry.isAutomationWriteTool(toolName)) {
                                conversation.setPhase(ConversationPhase.BUILDING);
                                conversationRepository.save(conversation);
                                currentPhase = ConversationPhase.BUILDING;
                                log.info("Phase transition: PLANNING → BUILDING (model invoked build tool {}, conversation {})",
                                        toolName, conversation.getId());
                                emitEvent(emitter, AiStreamEvent.phase(currentPhase.name(), conversationId));
                            }

                            Map<String, Object> toolResult = toolRegistry.executeTool(toolName, args, organizationId, userId, ipAddress, currentPhase);
                            boolean success = Boolean.TRUE.equals(toolResult.get("success"));

                            // Handle phase transitions from tool calls — AFTER the tool runs, so the
                            // propose_automation_plan call itself executes while still OPEN (the tool guard
                            // rejects it once the phase is PLANNING). Only transition on a successful proposal.
                            if (success && "propose_automation_plan".equals(toolName) && conversation.getPhase() == ConversationPhase.OPEN) {
                                conversation.setPhase(ConversationPhase.PLANNING);
                                conversationRepository.save(conversation);
                                currentPhase = ConversationPhase.PLANNING;
                                log.info("Phase transition: OPEN → PLANNING (conversation {})", conversation.getId());
                                emitEvent(emitter, AiStreamEvent.phase(currentPhase.name(), conversationId));
                            }

                            allToolCalls.add(new AiToolCallDto(toolName, args, toolResult.get("data"), success,
                                    toolResult.get("validationIssues")));
                            emitEvent(emitter, AiStreamEvent.toolResult(toolName, toolResult.get("data"), success,
                                    conversationId, toolResult.get("validationIssues")));

                            responseParts.add(Part.fromFunctionResponse(toolName, toolResult));
                        }

                        if (cancelled.get()) break;

                        if (!responseParts.isEmpty()) {
                            Content toolContent = Content.builder()
                                    .role("user")
                                    .parts(responseParts)
                                    .build();
                            contents.add(toolContent);
                        }
                    } else {
                        assistantReply = response.text();
                        emitEvent(emitter, AiStreamEvent.reply(assistantReply, conversationId));
                        break;
                    }
                }

                if (!cancelled.get()) {
                    if (assistantReply == null) {
                        assistantReply = defaultReply(!allToolCalls.isEmpty(), request.message(), request.language());
                        emitEvent(emitter, AiStreamEvent.reply(assistantReply, conversationId));
                    }

                    // Save conversation
                    Map<String, Object> assistantMsg = new LinkedHashMap<>();
                    assistantMsg.put("role", "assistant");
                    assistantMsg.put("content", assistantReply);
                    assistantMsg.put("timestamp", Instant.now().toString());
                    if (!allToolCalls.isEmpty()) {
                        assistantMsg.put("toolCalls", allToolCalls);
                    }
                    messageHistory.add(assistantMsg);

                    if (conversation.getTitle() == null || conversation.getTitle().isBlank()) {
                        String sanitized = request.message().replaceAll("<[^>]*>", "").trim();
                        String title = sanitized.length() > 50
                                ? sanitized.substring(0, 50) + "…"
                                : sanitized;
                        conversation.setTitle(title);
                    }

                    // Reset BUILDING → OPEN at end of turn (after all tool calls + final response)
                    if (conversation.getPhase() == ConversationPhase.BUILDING) {
                        conversation.setPhase(ConversationPhase.OPEN);
                        log.info("Phase transition: BUILDING → OPEN (turn completed, conversation {})", conversation.getId());
                        emitEvent(emitter, AiStreamEvent.phase("OPEN", conversationId));
                    }

                    conversation.setMessages(messageCodec.serialize(messageHistory));
                    conversationRepository.save(conversation);

                    auditService.log(userId, AuditAction.AI_CHAT,
                            "AI chat stream in conversation " + conversation.getId(), ipAddress);

                    emitEvent(emitter, AiStreamEvent.done(conversationId, conversation.getPhase().name()));
                }

                emitter.complete();
            } catch (Exception e) {
                log.error("SSE stream error for conversation {}: {}", conversationId, e.getMessage(), e);
                // Save partial conversation state so tool call results are not lost
                if (conversationId != null) {
                    try {
                        conversationRepository.findById(conversationId).ifPresent(conv -> {
                            // Preserve whatever messages were accumulated before the error
                            conversationRepository.save(conv);
                        });
                    } catch (Exception saveEx) {
                        log.warn("Failed to save partial conversation state for {}: {}", conversationId, saveEx.getMessage());
                    }
                }
                try {
                    if (conversationId != null) {
                        emitEvent(emitter, AiStreamEvent.error("An error occurred. Please try again.", conversationId));
                    }
                } catch (Exception emitEx) {
                    log.warn("Failed to emit SSE error event for conversation {}: {}", conversationId, emitEx.getMessage());
                }
                emitter.completeWithError(e);
            } finally {
                if (conversationId != null) {
                    activeSessions.remove(conversationId);
                }
                userStreamCounts.computeIfPresent(userId, (k, v) -> {
                    v.decrementAndGet();
                    return v;
                });
            }
        });

        return emitter;
    }

    @Override
    public void cancelChat(UUID userId, UUID conversationId) {
        // Verify the conversation belongs to this user before allowing cancellation
        boolean ownsConversation = conversationRepository.findByIdAndUserId(conversationId, userId).isPresent();
        if (!ownsConversation) {
            log.warn("User {} attempted to cancel conversation {} they do not own", userId, conversationId);
            return;
        }
        AtomicBoolean cancelled = activeSessions.get(conversationId);
        if (cancelled != null) {
            cancelled.set(true);
        }
    }

    private void emitEvent(SseEmitter emitter, AiStreamEvent event) {
        try {
            emitter.send(SseEmitter.event().data(event));
        } catch (IOException e) {
            log.warn("Failed to send SSE event: {}", e.getMessage());
        }
    }

    @Override
    public List<AiConversationListResponse> listConversations(UUID organizationId, UUID userId) {
        return conversationRepository.findTop100ByUserIdAndOrganizationIdOrderByUpdatedAtDesc(userId, organizationId)
                .stream()
                .map(c -> new AiConversationListResponse(c.getId(), c.getTitle(), c.getUpdatedAt()))
                .toList();
    }

    @Override
    public AiConversationDetailResponse getConversation(UUID organizationId, UUID userId, UUID conversationId) {
        AiConversation conversation = conversationRepository.findByIdAndUserIdAndOrganizationId(conversationId, userId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        List<Map<String, Object>> rawMessages = messageCodec.deserialize(conversation.getMessages());
        List<AiMessageDto> messages = rawMessages.stream()
                .map(messageCodec::toAiMessageDto)
                .toList();

        return new AiConversationDetailResponse(conversation.getId(), conversation.getTitle(), messages,
                conversation.getPhase().name());
    }

    @Override
    @Transactional
    public void deleteConversation(UUID organizationId, UUID userId, UUID conversationId) {
        AiConversation conversation = conversationRepository.findByIdAndUserIdAndOrganizationId(conversationId, userId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));
        conversation.setDeletedAt(Instant.now());
        conversationRepository.save(conversation);
    }

    /**
     * Calls Gemini with a small retry on transient failures (5xx / timeout / 429). Retries ONLY the
     * model call — never the surrounding tool-execution loop — so side-effecting tools never re-run.
     * Rethrows the last error after the attempts are exhausted (the caller then surfaces the error).
     */
    private GenerateContentResponse generateWithRetry(Client client, String model,
                                                      List<Content> contents, GenerateContentConfig config) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return client.models.generateContent(model, contents, config);
            } catch (RuntimeException e) {
                last = e;
                log.warn("Gemini call failed (attempt {}/3): {} — {}", attempt, e.getClass().getSimpleName(), e.getMessage());
                if (attempt < 3) {
                    try {
                        Thread.sleep(700L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw last;
    }

    /** Localized fallback used only when the model returns no text (rare). Mirrors the user's language. */
    private static String defaultReply(boolean hasToolCalls, String userMessage, String requestLanguage) {
        // Prefer the explicit UI language; only fall back to detecting from the message when absent
        // (the message may be a one-word button press that detects unreliably).
        String lang = normalizeLang(requestLanguage);
        if (lang == null) {
            lang = detectLang(userMessage);
        }
        if (hasToolCalls) {
            return switch (lang) {
                case "de" -> "Erledigt – die gewünschten Aktionen wurden ausgeführt. Details siehst du in den Tool-Aufrufen oben.";
                default -> "The requested operations have been completed. See the tool calls above for details.";
            };
        }
        return switch (lang) {
            case "de" -> "Entschuldigung, ich konnte deine Anfrage nicht verarbeiten. Bitte versuche es erneut.";
            default -> "Sorry, I could not process your request. Please try again.";
        };
    }

    /** Best-effort language detection (de/en) from the user's message, for the fallback reply only. */
    /** Normalizes a UI language code to {@code "de"}/{@code "en"}, or {@code null} if absent/unknown. */
    private static String normalizeLang(String language) {
        if (language == null || language.isBlank()) return null;
        return switch (language.toLowerCase().split("[-_]")[0]) {
            case "de" -> "de";
            case "en" -> "en";
            default -> null;
        };
    }

    private static String detectLang(String msg) {
        if (msg == null || msg.isBlank()) return "en";
        String m = msg.toLowerCase();
        if (m.matches("(?s).*[äöüß].*")
                || m.matches("(?s).*\\b(und|ich|eine|einen|nicht|bitte|erstelle|erstellen|sollen|mein|meine|kannst|für|wenn)\\b.*")) {
            return "de";
        }
        return "en";
    }

}
