package com.postwerk.controller;

import com.postwerk.dto.GeneratedSecretResponse;
import com.postwerk.dto.WebhookAuthRequest;
import com.postwerk.dto.WebhookEndpointResponse;
import com.postwerk.model.enums.Permission;
import com.postwerk.service.OrgContext;
import com.postwerk.service.OrgContextService;
import com.postwerk.service.WebhookEndpointService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for managing inbound webhook endpoints from the automation editor.
 *
 * <p>Exposes the generated public URL, token rotation, and authentication configuration
 * (auth mode, header name, signing secret). All routes are JWT-protected and org-scoped (#4):
 * reads require {@code AUTOMATION_VIEW}, mutations {@code AUTOMATION_EDIT}, so any authorized
 * member of the owning organization can manage an endpoint (not just its original creator).
 * The public ingress path ({@code /api/v1/hooks/{token}}) is unaffected.</p>
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/webhook-endpoints")
@Tag(name = "Webhook Endpoints", description = "Management of inbound webhook trigger endpoints")
public class WebhookEndpointController {

    private final WebhookEndpointService webhookEndpointService;
    private final OrgContextService orgContext;

    public WebhookEndpointController(WebhookEndpointService webhookEndpointService,
                                     OrgContextService orgContext) {
        this.webhookEndpointService = webhookEndpointService;
        this.orgContext = orgContext;
    }

    /** Returns the endpoint's URL, auth configuration, and trigger stats. */
    @GetMapping("/{id}")
    public ResponseEntity<WebhookEndpointResponse> get(
            OrgContext ctx,
            @PathVariable UUID id) {
        orgContext.require(ctx, Permission.AUTOMATION_VIEW);
        return ResponseEntity.ok(webhookEndpointService.get(ctx.organizationId(), id));
    }

    /** Rotates the URL token, invalidating the previous URL. */
    @PostMapping("/{id}/regenerate-token")
    public ResponseEntity<WebhookEndpointResponse> regenerateToken(
            OrgContext ctx,
            @PathVariable UUID id) {
        orgContext.require(ctx, Permission.AUTOMATION_EDIT);
        return ResponseEntity.ok(webhookEndpointService.regenerateToken(ctx.organizationId(), id));
    }

    /** Updates the authentication mode, header name, and (optionally) signing secret. */
    @PutMapping("/{id}/auth")
    public ResponseEntity<WebhookEndpointResponse> setAuth(
            OrgContext ctx,
            @PathVariable UUID id,
            @Valid @RequestBody WebhookAuthRequest request) {
        orgContext.require(ctx, Permission.AUTOMATION_EDIT);
        return ResponseEntity.ok(webhookEndpointService.setAuth(ctx.organizationId(), id, request));
    }

    /** Generates a fresh random signing secret, returned once and stored encrypted. */
    @PostMapping("/{id}/generate-secret")
    public ResponseEntity<GeneratedSecretResponse> generateSecret(
            OrgContext ctx,
            @PathVariable UUID id) {
        orgContext.require(ctx, Permission.AUTOMATION_EDIT);
        return ResponseEntity.ok(webhookEndpointService.generateSecret(ctx.organizationId(), id));
    }
}
