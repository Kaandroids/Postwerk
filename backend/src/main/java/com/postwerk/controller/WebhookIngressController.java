package com.postwerk.controller;

import com.postwerk.service.WebhookIngressService;
import com.postwerk.util.WebhookConstants;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public ingress endpoint for inbound webhook triggers.
 *
 * <p>External systems POST to {@code /api/v1/hooks/{token}} to trigger an automation.
 * The request body is mapped into {@code trigger.*} variables and the automation runs
 * synchronously, returning {@code 202 Accepted} with the execution id. A GET on the same
 * URL acts as a reachability ping. This endpoint is unauthenticated (JWT); per-endpoint
 * authentication is enforced via the configured auth mode (NONE / API_KEY / HMAC).</p>
 *
 * @since 1.0
 */
@RestController
@RequestMapping(WebhookConstants.HOOKS_BASE_PATH)
@Tag(name = "Webhook Ingress", description = "Public inbound webhook receivers for automation triggers")
public class WebhookIngressController {

    private final WebhookIngressService ingressService;

    public WebhookIngressController(WebhookIngressService ingressService) {
        this.ingressService = ingressService;
    }

    /** Receives an inbound webhook and triggers the linked automation. */
    @PostMapping("/{token}")
    public ResponseEntity<Map<String, Object>> receive(
            @PathVariable String token,
            @RequestBody(required = false) String body,
            HttpServletRequest request) {
        WebhookIngressService.IngestResult result =
                ingressService.ingest(token, body, request::getHeader);

        if (!result.accepted()) {
            return ResponseEntity.ok(Map.of("status", "ignored"));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("status", "accepted", "executionId", result.executionId().toString()));
    }

    /** Simple reachability ping for verifying the URL is live. */
    @GetMapping("/{token}")
    public ResponseEntity<Map<String, Object>> ping(@PathVariable String token) {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
