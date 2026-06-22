package com.postwerk.controller;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check endpoint for load balancer probes and container orchestration.
 *
 * <p>Performs shallow connectivity checks against PostgreSQL and Redis to ensure
 * the application and its critical dependencies are operational. Returns HTTP 200
 * when all checks pass, or HTTP 503 with failure details otherwise.</p>
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Application health check with database and Redis connectivity status")
public class HealthController {

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;

    public HealthController(DataSource dataSource, StringRedisTemplate redisTemplate) {
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
    }

    /** Returns the aggregated health status of the application and its dependencies. */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", Instant.now().toString());

        boolean dbUp = checkDatabase();
        boolean redisUp = checkRedis();
        boolean allUp = dbUp && redisUp;

        result.put("status", allUp ? "UP" : "DOWN");
        result.put("components", Map.of(
                "database", Map.of("status", dbUp ? "UP" : "DOWN"),
                "redis", Map.of("status", redisUp ? "UP" : "DOWN")
        ));

        return allUp
                ? ResponseEntity.ok(result)
                : ResponseEntity.status(503).body(result);
    }

    private boolean checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(3);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkRedis() {
        try {
            return Boolean.TRUE.equals(redisTemplate.execute((RedisCallback<Boolean>) connection -> {
                String pong = connection.ping();
                return "PONG".equalsIgnoreCase(pong);
            }));
        } catch (Exception e) {
            return false;
        }
    }
}
