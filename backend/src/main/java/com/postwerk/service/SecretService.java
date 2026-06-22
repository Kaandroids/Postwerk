package com.postwerk.service;

import com.postwerk.dto.SecretRequest;
import com.postwerk.dto.SecretResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for managing user-scoped secrets (API keys, tokens) stored with AES-256-GCM encryption.
 * Secrets can be referenced by automation nodes without exposing plaintext values.
 *
 * @since 1.0
 */
public interface SecretService {

    List<SecretResponse> list(UUID organizationId);

    SecretResponse create(UUID organizationId, UUID actingUserId, SecretRequest request);

    SecretResponse update(UUID organizationId, UUID id, SecretRequest request);

    void delete(UUID organizationId, UUID id);

    /** Decrypts a secret, scoped to the org (executor falls back to the actingUserId when org is null). */
    String resolveSecret(UUID secretId, UUID organizationId, UUID actingUserId);
}
