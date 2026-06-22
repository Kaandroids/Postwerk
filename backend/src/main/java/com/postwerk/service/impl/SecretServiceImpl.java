package com.postwerk.service.impl;

import com.postwerk.config.EncryptionConfig;
import com.postwerk.dto.SecretRequest;
import com.postwerk.dto.SecretResponse;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.Secret;
import com.postwerk.repository.SecretRepository;
import com.postwerk.service.SecretService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Default implementation of {@link SecretService}.
 * Stores and retrieves user secrets with AES-256-GCM encryption at rest.
 *
 * @since 1.0
 */
@Service
@Transactional
public class SecretServiceImpl implements SecretService {

    private final SecretRepository secretRepository;
    private final EncryptionConfig encryptionConfig;

    public SecretServiceImpl(SecretRepository secretRepository, EncryptionConfig encryptionConfig) {
        this.secretRepository = secretRepository;
        this.encryptionConfig = encryptionConfig;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SecretResponse> list(UUID organizationId) {
        return secretRepository.findAllByOrganizationId(organizationId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public SecretResponse create(UUID organizationId, UUID actingUserId, SecretRequest request) {
        if (secretRepository.existsByOrganizationIdAndName(organizationId, request.name())) {
            throw new IllegalArgumentException("A secret with this name already exists");
        }
        if (request.value() == null || request.value().isBlank()) {
            throw new IllegalArgumentException("Secret value must not be empty");
        }
        if (request.value().length() > 10000) {
            throw new IllegalArgumentException("Secret value too large");
        }
        Secret secret = new Secret();
        secret.setUserId(actingUserId);
        secret.setOrganizationId(organizationId);
        secret.setName(request.name());
        secret.setDescription(request.description());
        secret.setEncryptedValue(encryptionConfig.encrypt(request.value()));
        return toResponse(secretRepository.save(secret));
    }

    @Override
    public SecretResponse update(UUID organizationId, UUID id, SecretRequest request) {
        Secret secret = secretRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Secret", id));
        secret.setName(request.name());
        secret.setDescription(request.description());
        if (request.value() != null && !request.value().isBlank()) {
            secret.setEncryptedValue(encryptionConfig.encrypt(request.value()));
            secret.setVersion(secret.getVersion() + 1);
            secret.setLastRotatedAt(Instant.now());
        }
        return toResponse(secretRepository.save(secret));
    }

    @Override
    public void delete(UUID organizationId, UUID id) {
        Secret secret = secretRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Secret", id));
        secretRepository.delete(secret);
    }

    @Override
    @Transactional(readOnly = true)
    public String resolveSecret(UUID secretId, UUID organizationId, UUID actingUserId) {
        Secret secret = (organizationId != null
                ? secretRepository.findByIdAndOrganizationId(secretId, organizationId)
                : secretRepository.findByIdAndUserId(secretId, actingUserId))
                .orElseThrow(() -> new ResourceNotFoundException("Secret", secretId));
        return encryptionConfig.decrypt(secret.getEncryptedValue());
    }

    private SecretResponse toResponse(Secret secret) {
        return new SecretResponse(
                secret.getId(),
                secret.getName(),
                secret.getDescription(),
                secret.getVersion(),
                secret.getLastRotatedAt(),
                secret.getCreatedAt(),
                secret.getUpdatedAt()
        );
    }
}
