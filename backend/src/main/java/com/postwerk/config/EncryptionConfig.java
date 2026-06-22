package com.postwerk.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption service for securing sensitive data at rest.
 *
 * <p>Used primarily for encrypting IMAP/SMTP passwords before database storage.
 * The encryption key is loaded from the {@code ENCRYPTION_KEY} environment variable
 * (base64-encoded 32-byte key). Each encryption produces a unique IV, ensuring
 * identical plaintexts produce different ciphertexts.</p>
 *
 * @since 1.0
 */
@Component
public class EncryptionConfig {

    private static final Logger log = LoggerFactory.getLogger(EncryptionConfig.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    /** Base64 of the well-known shipped dev key {@code this-is-a-32-byte-dev-key!!!!!!!}. */
    private static final String DEV_KEY_BASE64 = "dGhpcy1pcy1hLTMyLWJ5dGUtZGV2LWtleSEhISEhISE=";

    private final SecretKeySpec keySpec;

    public EncryptionConfig(@Value("${app.encryption.key}") String base64Key, Environment environment) {
        boolean prod = java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (prod && DEV_KEY_BASE64.equals(base64Key == null ? null : base64Key.trim())) {
            throw new IllegalStateException(
                    "ENCRYPTION_KEY is still set to the insecure shipped dev key under the 'prod' profile. "
                            + "Generate a real key with: openssl rand -base64 32");
        }
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "Encryption key must be 256 bits (32 bytes), got " + keyBytes.length + " bytes");
        }
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = ByteBuffer.allocate(IV_LENGTH + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed");
        }
    }

    public String decrypt(String encrypted) {
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            ByteBuffer buffer = ByteBuffer.wrap(combined);

            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.debug("Decryption failed: {}", e.getMessage());
            throw new RuntimeException("Decryption failed");
        }
    }
}
