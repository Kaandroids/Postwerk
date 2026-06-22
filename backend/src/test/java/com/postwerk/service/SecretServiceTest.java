package com.postwerk.service;

import com.postwerk.config.EncryptionConfig;
import com.postwerk.dto.SecretRequest;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.Secret;
import com.postwerk.repository.SecretRepository;
import com.postwerk.service.impl.SecretServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecretServiceTest {

    @Mock private SecretRepository repository;
    @Mock private EncryptionConfig encryptionConfig;

    @InjectMocks
    private SecretServiceImpl service;

    private UUID orgId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void list_returnsAllSecretsForUser() {
        var s1 = createSecret(userId, "TOKEN_A");
        var s2 = createSecret(userId, "TOKEN_B");
        when(repository.findAllByOrganizationId(orgId)).thenReturn(List.of(s1, s2));

        var result = service.list(orgId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("TOKEN_A");
        assertThat(result.get(1).name()).isEqualTo("TOKEN_B");
    }

    @Test
    void list_neverReturnsEncryptedValue() {
        var s1 = createSecret(userId, "TOKEN_A");
        when(repository.findAllByOrganizationId(orgId)).thenReturn(List.of(s1));

        var result = service.list(orgId);

        // SecretResponse record has no value/encryptedValue field
        assertThat(result.get(0)).hasNoNullFieldsOrPropertiesExcept("description", "lastRotatedAt");
    }

    @Test
    void create_encryptsValueAndPersists() {
        var request = new SecretRequest("MY_TOKEN", "A description", "secret-value-123");
        when(encryptionConfig.encrypt("secret-value-123")).thenReturn("encrypted-xyz");
        when(repository.existsByOrganizationIdAndName(orgId, "MY_TOKEN")).thenReturn(false);
        when(repository.save(any(Secret.class))).thenAnswer(inv -> {
            Secret s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            s.setCreatedAt(Instant.now());
            s.setUpdatedAt(Instant.now());
            s.setVersion(1);
            return s;
        });

        var response = service.create(orgId, userId, request);

        assertThat(response.name()).isEqualTo("MY_TOKEN");
        assertThat(response.version()).isEqualTo(1);
        verify(encryptionConfig).encrypt("secret-value-123");
        verify(repository).save(argThat(s -> s.getEncryptedValue().equals("encrypted-xyz")));
    }

    @Test
    void create_duplicateName_throws() {
        var request = new SecretRequest("DUPLICATE", null, "val");
        when(repository.existsByOrganizationIdAndName(orgId, "DUPLICATE")).thenReturn(true);

        assertThatThrownBy(() -> service.create(orgId, userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void update_withNewValue_incrementsVersionAndRotates() {
        var existing = createSecret(userId, "TOKEN_A");
        existing.setVersion(2);
        UUID secretId = existing.getId();

        when(repository.findByIdAndOrganizationId(secretId, orgId)).thenReturn(Optional.of(existing));
        when(encryptionConfig.encrypt("new-value")).thenReturn("encrypted-new");
        when(repository.save(any(Secret.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new SecretRequest("TOKEN_A", "updated desc", "new-value");
        var response = service.update(orgId, secretId,request);

        assertThat(response.version()).isEqualTo(3);
        assertThat(response.lastRotatedAt()).isNotNull();
        verify(encryptionConfig).encrypt("new-value");
    }

    @Test
    void update_withBlankValue_keepsExistingEncryptedValue() {
        var existing = createSecret(userId, "TOKEN_A");
        existing.setVersion(1);
        existing.setEncryptedValue("old-encrypted");
        UUID secretId = existing.getId();

        when(repository.findByIdAndOrganizationId(secretId, orgId)).thenReturn(Optional.of(existing));
        when(repository.save(any(Secret.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new SecretRequest("TOKEN_A_RENAMED", "desc", "");
        var response = service.update(orgId, secretId,request);

        assertThat(response.name()).isEqualTo("TOKEN_A_RENAMED");
        assertThat(response.version()).isEqualTo(1); // not incremented
        verify(encryptionConfig, never()).encrypt(anyString());
        verify(repository).save(argThat(s -> s.getEncryptedValue().equals("old-encrypted")));
    }

    @Test
    void update_notFound_throws() {
        UUID secretId = UUID.randomUUID();
        when(repository.findByIdAndOrganizationId(secretId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(orgId, secretId,new SecretRequest("x", null, "v")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_removesSecret() {
        var existing = createSecret(userId, "TOKEN_A");
        UUID secretId = existing.getId();
        when(repository.findByIdAndOrganizationId(secretId, orgId)).thenReturn(Optional.of(existing));

        service.delete(orgId, secretId);

        verify(repository).delete(existing);
    }

    @Test
    void delete_notFound_throws() {
        UUID secretId = UUID.randomUUID();
        when(repository.findByIdAndOrganizationId(secretId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(orgId, secretId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resolveSecret_decryptsAndReturns() {
        var existing = createSecret(userId, "TOKEN_A");
        existing.setEncryptedValue("encrypted-abc");
        UUID secretId = existing.getId();

        when(repository.findByIdAndOrganizationId(secretId, orgId)).thenReturn(Optional.of(existing));
        when(encryptionConfig.decrypt("encrypted-abc")).thenReturn("plain-text-token");

        String resolved = service.resolveSecret(secretId, orgId, userId);

        assertThat(resolved).isEqualTo("plain-text-token");
        verify(encryptionConfig).decrypt("encrypted-abc");
    }

    @Test
    void resolveSecret_notFound_throws() {
        UUID secretId = UUID.randomUUID();
        when(repository.findByIdAndOrganizationId(secretId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveSecret(secretId, orgId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Secret createSecret(UUID userId, String name) {
        Secret s = new Secret();
        s.setId(UUID.randomUUID());
        s.setUserId(userId);
        s.setName(name);
        s.setEncryptedValue("enc-placeholder");
        s.setVersion(1);
        s.setCreatedAt(Instant.now());
        s.setUpdatedAt(Instant.now());
        return s;
    }
}
