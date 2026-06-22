package com.postwerk.aieval;

import com.postwerk.TestFixtures;
import com.postwerk.config.TestContainersConfig;
import com.postwerk.dto.AiChatRequest;
import com.postwerk.dto.AiChatResponse;
import com.postwerk.dto.AutomationDetailResponse;
import com.postwerk.dto.AutomationNodeDto;
import com.postwerk.dto.AutomationResponse;
import com.postwerk.dto.CategoryCandidate;
import com.postwerk.dto.ClassificationResult;
import com.postwerk.dto.WizardChatRequest;
import com.postwerk.dto.WizardSessionResponse;
import com.postwerk.dto.automation.AutomationValidationResult;
import com.postwerk.dto.automation.ValidationIssue;
import com.postwerk.model.Organization;
import com.postwerk.model.User;
import com.postwerk.model.enums.AutomationKind;
import com.postwerk.repository.OrganizationRepository;
import com.postwerk.repository.PlanRepository;
import com.postwerk.repository.UserRepository;
import com.postwerk.service.AiAssistantService;
import com.postwerk.service.AutomationService;
import com.postwerk.service.AutomationValidator;
import com.postwerk.service.GeminiService;
import com.postwerk.service.WizardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI WORKFLOW EVALUATION (live, opt-in) — sends real natural-language requests to BOTH AI surfaces
 * (post-login AI Chat assistant + public Wizard) and scores whether each produced a LOGICAL
 * automation workflow, via three gates:
 *   (a) structural validity  — AutomationValidator (0 error-severity issues)
 *   (b) shape / intent match  — expected node types are present
 *   (c) semantic judge        — LLM-as-judge (reuses GeminiService.match with PASS/FAIL candidates)
 *
 * This is an EVAL, not a CI gate: it RUNS each scenario and writes a pass-rate report to
 * test-proof/ai-eval-report.md. The JUnit assertion only checks the harness completed every
 * scenario — AI quality lives in the report.
 *
 * NOT picked up by the normal suite (no Test/IT suffix). Run deliberately with a real key:
 *   set -a; source .env; set +a
 *   ./mvnw test -Dsurefire.excludedGroups= -Dtest=AiWorkflowEvaluation
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@Tag("ai-eval")
class AiWorkflowEvaluation {

    @DynamicPropertySource
    static void realGemini(DynamicPropertyRegistry registry) {
        String key = System.getenv("GEMINI_API_KEY");
        if (key != null && !key.isBlank() && !"test-key".equals(key)) {
            registry.add("gemini.api-key", () -> key);
            registry.add("gemini.chat-model",
                    () -> System.getenv().getOrDefault("GEMINI_CHAT_MODEL", "gemini-2.5-flash"));
            registry.add("gemini.chat-temperature", () -> "0.1"); // determinism for eval
        }
    }

    @Autowired private AiAssistantService aiAssistantService;
    @Autowired private AutomationService automationService;
    @Autowired private AutomationValidator validator;
    @Autowired private WizardService wizardService;
    @Autowired private GeminiService geminiService;
    @Autowired private com.postwerk.service.AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private PlanRepository planRepository;
    @LocalServerPort private int port;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RedisTemplate<String, Object> redisTemplate;

    private static final String IP = "127.0.0.1";
    private static final String WIZARD_KEY_PREFIX = "wizard:session:";

    record Scenario(String id, String lang, String request, Set<String> expectedTypes, String intent) {}
    record JudgeVerdict(boolean pass, int confidence, String reason) {}
    record EvalResult(Scenario scenario, boolean built, boolean structurallyValid,
                      boolean shapeOk, Set<String> producedTypes,
                      List<ValidationIssue> issues, JudgeVerdict judge) {}
    record ScenarioStats(Scenario scenario, int n, int built, int valid, int shape,
                         int judge, int full, List<String> notes) {}

    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario("classify-label", "de",
                    "Wenn eine neue E-Mail in meinem Posteingang ankommt, kategorisiere sie automatisch "
                            + "und versieh sie mit einem passenden Label.",
                    Set.of("TRIGGER", "CATEGORIZE"),
                    "Automatically categorize incoming emails and apply labels"),
            new Scenario("forward-invoices", "de",
                    "Sobald eine Rechnung per E-Mail eintrifft, leite sie automatisch an "
                            + "buchhaltung@firma.de weiter.",
                    Set.of("TRIGGER", "EMAIL_ACTION"),
                    "Automatically forward invoice emails to the accounting department"),
            new Scenario("extract-webhook", "en",
                    "When an order confirmation email arrives, extract the order details and POST them "
                            + "to our webhook at https://hooks.example.com/orders.",
                    Set.of("TRIGGER", "EXTRACT", "WEBHOOK"),
                    "Extract order data from emails and push it to an external webhook")
    );

    @Test
    void evaluate() throws Exception {
        Assumptions.assumeTrue(realKeyPresent(),
                "Skipped — set a real GEMINI_API_KEY (source .env) to run the live AI eval.");

        int n = runs();
        List<ScenarioStats> chat = new ArrayList<>();
        for (Scenario s : SCENARIOS) {
            chat.add(aggregate(s, n, () -> runChatScenario(s)));
        }

        // One AI-enabled org/user for the wizard judge calls (wizard build itself is public/unmetered).
        UUID[] judge = enterpriseUser("aieval-wizard-judge");
        List<ScenarioStats> wizard = new ArrayList<>();
        for (Scenario s : SCENARIOS) {
            wizard.add(aggregate(s, n, () -> runWizardScenario(s, judge[0], judge[1])));
        }

        writeReport(n, chat, wizard);
        assertThat(chat).hasSize(SCENARIOS.size());
        assertThat(wizard).hasSize(SCENARIOS.size());
    }

    /** Runs one scenario N times and tallies how many runs passed each gate (pass-rate). */
    private ScenarioStats aggregate(Scenario s, int n, java.util.function.Supplier<EvalResult> run) {
        int built = 0, valid = 0, shape = 0, judge = 0, full = 0;
        List<String> notes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            EvalResult r = run.get();
            boolean ok = r.built() && r.structurallyValid() && r.shapeOk() && r.judge().pass();
            if (r.built()) built++;
            if (r.structurallyValid()) valid++;
            if (r.shapeOk()) shape++;
            if (r.judge().pass()) judge++;
            if (ok) full++;
            if (!ok && notes.size() < 3) notes.add(failureNote(r));
        }
        return new ScenarioStats(s, n, built, valid, shape, judge, full, notes);
    }

    private String failureNote(EvalResult r) {
        if (!r.built()) return r.judge().reason();
        if (!r.structurallyValid()) return "validator: " + r.issues().stream()
                .filter(ValidationIssue::isError).map(ValidationIssue::code).collect(Collectors.joining(","));
        if (!r.shapeOk()) return "shape: produced " + r.producedTypes();
        return "judge: " + r.judge().reason();
    }

    private int runs() {
        try {
            return Math.max(1, Integer.parseInt(System.getenv().getOrDefault("AI_EVAL_RUNS", "5")));
        } catch (NumberFormatException e) {
            return 5;
        }
    }

    // ── Surface A: post-login AI Chat ───────────────────────────────────────────
    private EvalResult runChatScenario(Scenario s) {
        UUID[] uo = enterpriseUser("aieval-chat-" + s.id);
        UUID orgId = uo[0], userId = uo[1];
        Set<UUID> before = listIds(orgId);
        try {
            AiChatResponse r1 = aiAssistantService.chat(orgId, userId,
                    new AiChatRequest(s.request, null, null, null), IP);
            if ("PLANNING".equalsIgnoreCase(r1.phase())) {
                aiAssistantService.chat(orgId, userId,
                        new AiChatRequest("Ja, bau es bitte genau so.", r1.conversationId(), null, null), IP);
            }
        } catch (Exception e) {
            return fail(s, "chat error: " + e.getMessage());
        }
        Set<UUID> after = listIds(orgId);
        after.removeAll(before);
        if (after.isEmpty()) return fail(s, "AI did not build an automation");

        UUID automationId = after.iterator().next();
        AutomationValidationResult vr = automationService.validate(orgId, automationId);
        AutomationDetailResponse detail = automationService.getById(orgId, automationId);
        Set<String> types = detail.nodes().stream().map(AutomationNodeDto::nodeType).collect(Collectors.toSet());
        String summary = detail.nodes().stream()
                .map(n -> n.nodeType() + cfg(n.config())).collect(Collectors.joining("  →  "));
        JudgeVerdict judge = judge(orgId, userId, s, summary);
        return new EvalResult(s, true, vr.valid(), types.containsAll(s.expectedTypes), types, vr.issues(), judge);
    }

    // ── Surface B: public Wizard (SSE) ──────────────────────────────────────────
    private EvalResult runWizardScenario(Scenario s, UUID judgeOrg, UUID judgeUser) {
        String[] followups = {
                "Ja, genau so. Bitte setze diesen Workflow jetzt um.",
                "Ja, bitte den Workflow vollständig bauen.",
                "Passt perfekt, fertigstellen."
        };
        try {
            // The wizard generates its own session id; capture it by diffing Redis session keys
            // around the first turn (SSE body field is "conversationId", not parseable as sessionId).
            Set<String> keysBefore = wizardKeys();
            postWizard(null, s.request, s.lang);
            UUID sessionId = newWizardSession(keysBefore);
            if (sessionId == null) return fail(s, "wizard did not create a session");

            Map<String, Object> plan = planOf(sessionId);
            for (int i = 0; !hasNodes(plan) && i < followups.length; i++) {
                postWizard(sessionId, followups[i], s.lang);
                plan = planOf(sessionId);
            }
            if (!hasNodes(plan)) return fail(s, "wizard did not build a flow");

            List<AutomationValidator.NodeView> nodes = nodeViews(plan);
            List<AutomationValidator.EdgeView> edges = edgeViews(plan);
            AutomationValidationResult vr = validator.validate(AutomationKind.AUTOMATION, nodes, edges, Set.of());
            Set<String> types = nodes.stream().map(AutomationValidator.NodeView::nodeType).collect(Collectors.toSet());
            String summary = nodes.stream()
                    .map(n -> n.nodeType() + cfg(n.config())).collect(Collectors.joining("  →  "));
            JudgeVerdict judge = judge(judgeOrg, judgeUser, s, summary);
            return new EvalResult(s, true, vr.valid(), types.containsAll(s.expectedTypes), types, vr.issues(), judge);
        } catch (Exception e) {
            return fail(s, "wizard error: " + e.getMessage());
        }
    }

    /**
     * Drive one wizard turn over a REAL HTTP connection and return the full SSE body. A real
     * connection is required: the wizard streams events from a virtual-thread executor, so MockMvc's
     * async-dispatch would not capture them. ofString() blocks until the emitter completes the turn.
     */
    private String postWizard(UUID sessionId, String message, String lang) throws Exception {
        WizardChatRequest req = new WizardChatRequest(sessionId, message, lang);
        HttpClient http = HttpClient.newHttpClient();
        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/wizard/chat"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(3))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(req)))
                .build();
        HttpResponse<String> resp = http.send(httpReq, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    private Set<String> wizardKeys() {
        Set<String> keys = redisTemplate.keys(WIZARD_KEY_PREFIX + "*");
        return keys == null ? new HashSet<>() : new HashSet<>(keys);
    }

    /** The session key that appeared since {@code before} → its UUID. Retries to absorb the
     *  small race between the HTTP turn returning and the session being flushed to Redis. */
    private UUID newWizardSession(Set<String> before) {
        for (int attempt = 0; attempt < 4; attempt++) {
            Set<String> after = wizardKeys();
            after.removeAll(before);
            if (!after.isEmpty()) {
                return UUID.fromString(after.iterator().next().substring(WIZARD_KEY_PREFIX.length()));
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> planOf(UUID sessionId) {
        WizardSessionResponse s = wizardService.getSession(sessionId);
        return s == null ? Map.of() : (s.automationPlan() == null ? Map.of() : s.automationPlan());
    }

    @SuppressWarnings("unchecked")
    private boolean hasNodes(Map<String, Object> plan) {
        Object nodes = plan.get("nodes");
        return nodes instanceof List<?> l && !l.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private List<AutomationValidator.NodeView> nodeViews(Map<String, Object> plan) {
        List<Map<String, Object>> raw = (List<Map<String, Object>>) plan.getOrDefault("nodes", List.of());
        List<AutomationValidator.NodeView> out = new ArrayList<>();
        for (Map<String, Object> n : raw) {
            out.add(new AutomationValidator.NodeView(
                    str(n.get("id")), str(n.get("nodeType")), str(n.get("label")), configJson(n.get("config")),
                    str(n.get("id"))));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<AutomationValidator.EdgeView> edgeViews(Map<String, Object> plan) {
        List<Map<String, Object>> raw = (List<Map<String, Object>>) plan.getOrDefault("edges", List.of());
        List<AutomationValidator.EdgeView> out = new ArrayList<>();
        for (Map<String, Object> e : raw) {
            out.add(new AutomationValidator.EdgeView(
                    str(e.get("sourceNodeId")), str(e.get("sourceHandle")),
                    str(e.get("targetNodeId")), str(e.get("targetHandle"))));
        }
        return out;
    }

    private String configJson(Object config) {
        if (config == null) return "{}";
        if (config instanceof String str) return str.isBlank() ? "{}" : str;
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            return "{}";
        }
    }

    // ── Shared: LLM-as-judge via GeminiService.match ────────────────────────────
    private JudgeVerdict judge(UUID orgId, UUID userId, Scenario s, String flowSummary) {
        UUID passId = UUID.randomUUID();
        UUID failId = UUID.randomUUID();
        String query = "User goal: \"" + s.intent + "\". The AI built this automation flow: ["
                + flowSummary + "]. Does this workflow correctly and completely achieve the user's goal?";
        List<CategoryCandidate> candidates = List.of(
                new CategoryCandidate(passId, "Achieves the goal",
                        "The workflow's node types and configuration correctly implement what the user asked for.", "", ""),
                new CategoryCandidate(failId, "Does not achieve the goal",
                        "The workflow is missing required steps or is misconfigured for the user's request.", "", ""));
        try {
            ClassificationResult res = geminiService.match(orgId, userId, query, candidates);
            return new JudgeVerdict(passId.toString().equals(res.categoryId()), res.confidence(), res.reason());
        } catch (Exception e) {
            return new JudgeVerdict(false, 0, "judge error: " + e.getMessage());
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────
    /** Registers a fresh user, returns {orgId, userId}, and puts the org on ENTERPRISE (unlimited AI). */
    private UUID[] enterpriseUser(String prefix) {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        authService.register(TestFixtures.createRegisterRequest(email), IP);
        User user = userRepository.findByEmail(email).orElseThrow();
        Organization org = organizationRepository.findByOwnerUserIdAndPersonalTrue(user.getId()).orElseThrow();
        org.setPlan(planRepository.findByName("ENTERPRISE").orElseThrow());
        organizationRepository.save(org);
        return new UUID[]{org.getId(), user.getId()};
    }

    private Set<UUID> listIds(UUID orgId) {
        return automationService.listByOrg(orgId).stream()
                .map(AutomationResponse::id).collect(Collectors.toSet());
    }

    private EvalResult fail(Scenario s, String reason) {
        return new EvalResult(s, false, false, false, Set.of(), List.of(), new JudgeVerdict(false, 0, reason));
    }

    private boolean realKeyPresent() {
        String key = System.getenv("GEMINI_API_KEY");
        return key != null && !key.isBlank() && !"test-key".equals(key);
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }

    private static String cfg(String config) {
        return (config == null || config.isBlank() || "{}".equals(config)) ? "" : " " + config;
    }

    private void writeReport(int n, List<ScenarioStats> chat, List<ScenarioStats> wizard) throws Exception {
        StringBuilder md = new StringBuilder();
        md.append("# AI Workflow Evaluation — does the AI build a logical workflow? (live)\n\n");
        md.append("> Live Gemini · **").append(n).append(" runs** per scenario (pass-rate captures non-determinism). ")
                .append("3 gates: **structural validity** (AutomationValidator) · **shape** (expected node types) · ")
                .append("**semantic judge** (LLM).\n\n");
        section(md, "Surface A — AI Chat (post-login)", n, chat);
        section(md, "Surface B — Wizard (getstarted)", n, wizard);

        Path out = Paths.get(System.getProperty("user.dir")).resolveSibling("test-proof")
                .resolve("ai-eval-report.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, md.toString());
    }

    private void section(StringBuilder md, String title, int n, List<ScenarioStats> stats) {
        md.append("## ").append(title).append("\n\n");
        md.append("| Scenario | Built | Valid | Shape | Judge | **Full pass** |\n");
        md.append("|---|---|---|---|---|---|\n");
        for (ScenarioStats s : stats) {
            md.append("| `").append(s.scenario().id()).append("` | ")
                    .append(rate(s.built(), n)).append(" | ")
                    .append(rate(s.valid(), n)).append(" | ")
                    .append(rate(s.shape(), n)).append(" | ")
                    .append(rate(s.judge(), n)).append(" | **")
                    .append(rate(s.full(), n)).append("** |\n");
        }
        md.append("\n");
        for (ScenarioStats s : stats) {
            if (s.full() == n) continue; // 100% — no failure notes needed
            md.append("- **`").append(s.scenario().id()).append("`** failure examples: ")
                    .append(s.notes().isEmpty() ? "—" : String.join("  ·  ", s.notes())).append("\n");
        }
        md.append("\n");
    }

    private static String rate(int x, int n) {
        return x + "/" + n + " (" + Math.round(100.0 * x / n) + "%)";
    }
}
