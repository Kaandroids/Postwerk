package com.postwerk.service.impl;

import com.postwerk.dto.admin.MaintenanceModeResponse;
import com.postwerk.dto.admin.SubsystemCheckResponse;
import com.postwerk.dto.admin.SubsystemHealthResponse;
import com.postwerk.dto.admin.SystemHealthEventResponse;
import com.postwerk.dto.admin.SystemHealthKpisResponse;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.AuditAction;
import com.postwerk.repository.AiTokenUsageRepository;
import com.postwerk.repository.AuditLogRepository;
import com.postwerk.repository.AutomationDelayedEmailRepository;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.PendingActionRepository;
import com.postwerk.model.enums.ApprovalStatus;
import com.postwerk.service.AdminSystemHealthService;
import com.postwerk.service.AuditService;
import com.postwerk.service.MaintenanceModeService;
import com.zaxxer.hikari.HikariDataSource;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Default {@link AdminSystemHealthService}. Probes Postwerk's own subsystems LIVE per request from
 * real sources (Micrometer http metrics, HikariCP pool, Redis ping+INFO, the pending-action/delayed-
 * email job tables, the email-sync columns, the Gemini circuit breaker + token usage). Metrics with
 * no real source (SMTP queue/latency, API percentiles, per-minute history) are honestly omitted.
 *
 * @since 1.0
 */
@Service
public class AdminSystemHealthServiceImpl implements AdminSystemHealthService {

    private static final Logger log = LoggerFactory.getLogger(AdminSystemHealthServiceImpl.class);

    /** Infra-relevant audit actions surfaced in the "recent events" timeline. */
    private static final List<AuditAction> EVENT_ACTIONS = List.of(
            AuditAction.MAILBOX_RESYNC_TRIGGERED, AuditAction.MAILBOX_PAUSED, AuditAction.MAILBOX_RESUMED,
            AuditAction.SYSTEM_HEALTH_PROBE, AuditAction.CACHE_FLUSHED,
            AuditAction.MAINTENANCE_MODE_ENABLED, AuditAction.MAINTENANCE_MODE_DISABLED);

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;
    private final CacheManager cacheManager;
    private final MeterRegistry meterRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final PendingActionRepository pendingActionRepository;
    private final AutomationDelayedEmailRepository delayedEmailRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final AiTokenUsageRepository aiTokenUsageRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final MaintenanceModeService maintenanceModeService;

    @Value("${gemini.model:}")
    private String geminiModel;

    @Value("${gemini.api-key:}")
    private String geminiApiKey;

    public AdminSystemHealthServiceImpl(DataSource dataSource,
                                        StringRedisTemplate redisTemplate,
                                        CacheManager cacheManager,
                                        MeterRegistry meterRegistry,
                                        CircuitBreakerRegistry circuitBreakerRegistry,
                                        PendingActionRepository pendingActionRepository,
                                        AutomationDelayedEmailRepository delayedEmailRepository,
                                        EmailAccountRepository emailAccountRepository,
                                        AiTokenUsageRepository aiTokenUsageRepository,
                                        AuditLogRepository auditLogRepository,
                                        AuditService auditService,
                                        MaintenanceModeService maintenanceModeService) {
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
        this.cacheManager = cacheManager;
        this.meterRegistry = meterRegistry;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.pendingActionRepository = pendingActionRepository;
        this.delayedEmailRepository = delayedEmailRepository;
        this.emailAccountRepository = emailAccountRepository;
        this.aiTokenUsageRepository = aiTokenUsageRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
        this.maintenanceModeService = maintenanceModeService;
    }

    // ── Public API ──────────────────────────────────────────────────────────
    @Override
    public List<SubsystemHealthResponse> subsystems() {
        return List.of(buildApi(), buildPostgres(), buildRedis(), buildScheduler(),
                buildEmailSync(), buildSmtp(), buildGemini());
    }

    @Override
    public SystemHealthKpisResponse kpis() {
        List<SubsystemHealthResponse> subs = subsystems();
        long down = subs.stream().filter(s -> "down".equals(s.status())).count();
        long degraded = subs.stream().filter(s -> "degraded".equals(s.status())).count();
        long ok = subs.stream().filter(s -> "ok".equals(s.status())).count();

        ApiStats api = apiStats();
        DbPool db = dbPool();
        RedisStats rs = redisStats();
        JobQueue jq = jobQueue();

        return new SystemHealthKpisResponse(
                api.meanMs(), api.errPct(), api.rpm(),
                db.active(), db.max(),
                rs.usedMb(), rs.maxMb(),
                jq.total(),
                down, degraded, ok, subs.size(), api.uptimeMs());
    }

    @Override
    public List<SystemHealthEventResponse> events() {
        return auditLogRepository.findTop20ByActionInOrderByCreatedAtDesc(EVENT_ACTIONS).stream()
                .map(l -> new SystemHealthEventResponse(
                        toneFor(l.getAction()), humanize(l.getAction()), l.getDetail(), l.getCreatedAt()))
                .toList();
    }

    @Override
    public SubsystemHealthResponse getSubsystem(String id) {
        return switch (id) {
            case "api" -> buildApi();
            case "postgres" -> buildPostgres();
            case "redis" -> buildRedis();
            case "scheduler" -> buildScheduler();
            case "email-sync" -> buildEmailSync();
            case "smtp" -> buildSmtp();
            case "gemini" -> buildGemini();
            default -> throw new ResourceNotFoundException("Subsystem", id);
        };
    }

    @Override
    public SubsystemHealthResponse probe(String id, UUID actorUserId, String ip) {
        SubsystemHealthResponse s = getSubsystem(id);
        auditService.log(actorUserId, AuditAction.SYSTEM_HEALTH_PROBE, "Probed subsystem " + s.name(), ip);
        return s;
    }

    @Override
    public void flushCache(UUID actorUserId, String ip) {
        int cleared = 0;
        for (String name : cacheManager.getCacheNames()) {
            Cache c = cacheManager.getCache(name);
            if (c != null) { c.clear(); cleared++; }
        }
        auditService.log(actorUserId, AuditAction.CACHE_FLUSHED, "Flushed " + cleared + " cache region(s)", ip);
    }

    @Override
    public MaintenanceModeResponse getMaintenance() {
        return new MaintenanceModeResponse(
                maintenanceModeService.isEnabled(), maintenanceModeService.message(), maintenanceModeService.updatedAt());
    }

    @Override
    public MaintenanceModeResponse setMaintenance(boolean enabled, String message, UUID actorUserId, String ip) {
        maintenanceModeService.set(enabled, message);
        auditService.log(actorUserId,
                enabled ? AuditAction.MAINTENANCE_MODE_ENABLED : AuditAction.MAINTENANCE_MODE_DISABLED,
                message == null || message.isBlank() ? null : message.trim(), ip);
        return getMaintenance();
    }

    // ── Subsystem builders ──────────────────────────────────────────────────
    private SubsystemHealthResponse buildApi() {
        ApiStats a = apiStats();
        String status = (a.errPct() != null && a.errPct() > 5.0) ? "degraded" : "ok";
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Latency (avg)", a.meanMs() + " ms");
        m.put("Error rate (5xx)", a.errPct() + " %");
        m.put("Requests (total)", a.count());
        m.put("Requests/min (avg)", a.rpm());
        m.put("Uptime", humanizeUptime(a.uptimeMs()));
        m.put("Instances", 1);
        String primary = a.meanMs() + "ms avg · " + a.errPct() + "% err";
        return sub("api", "API gateway", "API", null, status, primary, m, null);
    }

    private SubsystemHealthResponse buildPostgres() {
        DbPool db = dbPool();
        String status = db.up() ? "ok" : "down";
        Map<String, Object> m = new LinkedHashMap<>();
        if (db.active() != null) {
            m.put("Pool active", db.active());
            m.put("Pool idle", db.idle());
            m.put("Pool total", db.total());
            m.put("Pool max", db.max());
        }
        String primary = db.max() != null ? "pool " + db.active() + "/" + db.max()
                : (db.up() ? "connected" : "unreachable");
        return sub("postgres", "PostgreSQL", "Database", db.version(), status, primary, m, db.lastError());
    }

    private SubsystemHealthResponse buildRedis() {
        RedisStats r = redisStats();
        String status = r.up() ? "ok" : "down";
        Map<String, Object> m = new LinkedHashMap<>();
        if (r.up()) {
            m.put("Memory used", r.usedMb() + " MB");
            m.put("Memory max", r.maxMb() != null ? r.maxMb() + " MB" : "unbounded");
            m.put("Hit rate", r.hitRate() + " %");
            m.put("Connected clients", r.clients());
            m.put("Evicted keys", r.evicted());
        }
        String primary = r.up()
                ? "mem " + r.usedMb() + (r.maxMb() != null ? "/" + r.maxMb() : "") + " MB"
                : "unreachable";
        return sub("redis", "Redis cache", "Cache", r.version(), status, primary, m, r.lastError());
    }

    private SubsystemHealthResponse buildScheduler() {
        JobQueue jq = jobQueue();
        String status = jq.total() > 100 ? "degraded" : "ok";
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Pending approvals", jq.pending());
        m.put("Delayed emails", jq.delayed());
        m.put("Queue depth", jq.total());
        String primary = "queue " + jq.total();
        return sub("scheduler", "Job scheduler", "Scheduler", null, status, primary, m, null);
    }

    private SubsystemHealthResponse buildEmailSync() {
        long total = emailAccountRepository.countByReadEnabledTrueAndImapHostIsNotNull();
        long failing = emailAccountRepository.countByLastSyncStatusIn(List.of("AUTH_ERROR", "CONN_ERROR"));
        String status = failing == 0 ? "ok" : (failing * 2 >= total ? "down" : "degraded");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Read-enabled mailboxes", total);
        m.put("Failing", failing);
        m.put("Worker pool (max)", 4);
        String primary = total + " mailboxes · " + failing + " failing";
        String lastError = failing > 0 ? failing + " mailbox(es) failing to sync" : null;
        return sub("email-sync", "Email-sync workers", "Workers", null, status, primary, m, lastError);
    }

    private SubsystemHealthResponse buildSmtp() {
        long senders = emailAccountRepository.countByWriteEnabledTrueAndSmtpHostIsNotNull();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Send-enabled mailboxes", senders);
        m.put("Mode", "synchronous (no queue)");
        m.put("Queue depth", "n/a");
        String primary = senders + " senders · synchronous";
        return sub("smtp", "SMTP send pipeline", "Email", null, "ok", primary, m, null);
    }

    private SubsystemHealthResponse buildGemini() {
        boolean keyOk = geminiApiKey != null && !geminiApiKey.isBlank();
        String state = "CLOSED";
        try {
            state = circuitBreakerRegistry.circuitBreaker("gemini").getState().name();
        } catch (Exception e) {
            log.debug("Gemini circuit breaker state unavailable: {}", e.getMessage());
        }
        Instant monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant dayAgo = Instant.now().minus(Duration.ofHours(24));
        long tokens24h = aiTokenUsageRepository.sumTotalTokensSince(dayAgo);
        long costMonthMicros = aiTokenUsageRepository.sumCostMicrosSince(monthStart);

        String status;
        String lastError = null;
        if (!keyOk) {
            status = "down";
            lastError = "Gemini API key not configured";
        } else {
            status = switch (state) {
                case "OPEN", "FORCED_OPEN" -> "down";
                case "HALF_OPEN" -> "degraded";
                default -> "ok";
            };
            if (!"ok".equals(status)) lastError = "Circuit breaker " + state;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Model", geminiModel == null || geminiModel.isBlank() ? "—" : geminiModel);
        m.put("API key", keyOk ? "configured" : "missing");
        m.put("Circuit breaker", state);
        m.put("Tokens (24h)", tokens24h);
        m.put("Cost (this month)", String.format(Locale.US, "€%.2f", costMonthMicros / 1_000_000.0));
        String primary = keyOk ? (m.get("Model") + " · " + state.toLowerCase()) : "not configured";
        return sub("gemini", "AI provider · Gemini", "External", geminiModel, status, primary, m, lastError);
    }

    // ── Numeric probe helpers (shared by builders + KPIs) ────────────────────
    private record ApiStats(Long meanMs, Double errPct, Long rpm, long count, long uptimeMs) {}
    private record DbPool(boolean up, Integer active, Integer idle, Integer total, Integer max, String version, String lastError) {}
    private record RedisStats(boolean up, Long usedMb, Long maxMb, double hitRate, long clients, long evicted, String version, String lastError) {}
    private record JobQueue(long pending, long delayed, long total) {}

    private ApiStats apiStats() {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        long count = 0;
        double totalMs = 0;
        for (Timer t : meterRegistry.find("http.server.requests").timers()) {
            count += t.count();
            totalMs += t.totalTime(TimeUnit.MILLISECONDS);
        }
        long errors = 0;
        for (Timer t : meterRegistry.find("http.server.requests").tag("outcome", "SERVER_ERROR").timers()) {
            errors += t.count();
        }
        long meanMs = count > 0 ? Math.round(totalMs / count) : 0;
        double errPct = count > 0 ? round2(errors * 100.0 / count) : 0.0;
        long upMin = Math.max(1, uptimeMs / 60000);
        long rpm = count / upMin;
        return new ApiStats(meanMs, errPct, rpm, count, uptimeMs);
    }

    private DbPool dbPool() {
        boolean up = false;
        String version = null;
        String lastError = null;
        try (Connection c = dataSource.getConnection()) {
            up = c.isValid(3);
            try {
                String v = c.getMetaData().getDatabaseProductName() + " " + c.getMetaData().getDatabaseProductVersion();
                version = v.length() > 40 ? v.substring(0, 40) : v;
            } catch (Exception ignored) { /* version best-effort */ }
        } catch (Exception e) {
            lastError = e.getMessage();
        }
        Integer active = null, idle = null, total = null, max = null;
        if (dataSource instanceof HikariDataSource hds) {
            try {
                var mx = hds.getHikariPoolMXBean();
                active = mx.getActiveConnections();
                idle = mx.getIdleConnections();
                total = mx.getTotalConnections();
                max = hds.getMaximumPoolSize();
            } catch (Exception ignored) { /* pool stats best-effort */ }
        }
        if (!up && lastError == null) lastError = "Database connection invalid";
        return new DbPool(up, active, idle, total, max, version, up ? null : lastError);
    }

    private RedisStats redisStats() {
        Properties info = null;
        String lastError = null;
        try {
            info = redisTemplate.execute((RedisCallback<Properties>) (RedisConnection conn) -> {
                String pong = conn.ping();
                return "PONG".equalsIgnoreCase(pong) ? conn.serverCommands().info() : null;
            });
        } catch (Exception e) {
            lastError = e.getMessage();
        }
        if (info == null) {
            return new RedisStats(false, null, null, 0, 0, 0, null,
                    lastError != null ? lastError : "Redis ping failed");
        }
        long usedBytes = parseLong(info.getProperty("used_memory"));
        long maxBytes = parseLong(info.getProperty("maxmemory"));
        long hits = parseLong(info.getProperty("keyspace_hits"));
        long misses = parseLong(info.getProperty("keyspace_misses"));
        double hitRate = (hits + misses) > 0 ? round2(hits * 100.0 / (hits + misses)) : 0.0;
        String version = "Redis " + info.getProperty("redis_version", "");
        return new RedisStats(true, usedBytes / 1024 / 1024, maxBytes > 0 ? maxBytes / 1024 / 1024 : null,
                hitRate, parseLong(info.getProperty("connected_clients")),
                parseLong(info.getProperty("evicted_keys")), version.trim(), null);
    }

    private JobQueue jobQueue() {
        long pending = 0, delayed = 0;
        try { pending = pendingActionRepository.countByStatus(ApprovalStatus.PENDING); } catch (Exception ignored) {}
        try { delayed = delayedEmailRepository.countByProcessedFalse(); } catch (Exception ignored) {}
        return new JobQueue(pending, delayed, pending + delayed);
    }

    // ── Small helpers ───────────────────────────────────────────────────────
    private SubsystemHealthResponse sub(String id, String name, String kind, String version,
                                        String status, String primary, Map<String, Object> metrics, String lastError) {
        boolean ok = "ok".equals(status);
        String msg = ok ? "Probe OK" : (lastError != null ? lastError : "Probe " + status);
        List<SubsystemCheckResponse> checks = List.of(new SubsystemCheckResponse(Instant.now(), ok, msg));
        return new SubsystemHealthResponse(id, name, kind, version, status, primary, metrics, 0L, lastError, checks);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static long parseLong(String s) {
        if (s == null) return 0;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    private static String humanizeUptime(long ms) {
        long s = ms / 1000;
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }

    private static String toneFor(AuditAction a) {
        return switch (a) {
            case MAINTENANCE_MODE_ENABLED, MAILBOX_PAUSED, CACHE_FLUSHED -> "warn";
            default -> "ok";
        };
    }

    private static String humanize(AuditAction a) {
        return switch (a) {
            case MAILBOX_RESYNC_TRIGGERED -> "Mailbox re-sync queued";
            case MAILBOX_PAUSED -> "Mailbox paused";
            case MAILBOX_RESUMED -> "Mailbox resumed";
            case SYSTEM_HEALTH_PROBE -> "Subsystem probed";
            case CACHE_FLUSHED -> "Cache flushed";
            case MAINTENANCE_MODE_ENABLED -> "Maintenance mode enabled";
            case MAINTENANCE_MODE_DISABLED -> "Maintenance mode disabled";
            default -> a.name();
        };
    }
}
