package com.postwerk.service;

import com.postwerk.dto.GeneratedSecretResponse;
import com.postwerk.dto.WebhookAuthRequest;
import com.postwerk.dto.WebhookEndpointResponse;

import java.util.UUID;

/**
 * Service for managing inbound webhook endpoints from the automation editor:
 * inspecting the generated URL, rotating the token, and configuring authentication.
 *
 * <p>All operations are scoped to the caller's active organization (resolved from the request's
 * {@code OrgContext}), matching the org-scoping model applied to the owning automation.</p>
 *
 * @since 1.0
 */
public interface WebhookEndpointService {

    WebhookEndpointResponse get(UUID organizationId, UUID id);

    WebhookEndpointResponse regenerateToken(UUID organizationId, UUID id);

    WebhookEndpointResponse setAuth(UUID organizationId, UUID id, WebhookAuthRequest request);

    GeneratedSecretResponse generateSecret(UUID organizationId, UUID id);
}
