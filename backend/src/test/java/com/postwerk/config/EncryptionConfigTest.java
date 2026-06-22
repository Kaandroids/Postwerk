package com.postwerk.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

class EncryptionConfigTest {

    private EncryptionConfig encryption;

    // Valid 32-byte key encoded in base64
    private static final String VALID_KEY = "dGhpcy1pcy1hLTMyLWJ5dGUtZGV2LWtleSEhISEhISE=";

    @BeforeEach
    void setUp() {
        encryption = new EncryptionConfig(VALID_KEY, new MockEnvironment());
    }

    @Test
    void encrypt_validPlaintext_returnsBase64() {
        String ciphertext = encryption.encrypt("hello world");
        assertThat(ciphertext).isNotBlank();
        assertThatCode(() -> Base64.getDecoder().decode(ciphertext))
                .doesNotThrowAnyException();
    }

    @Test
    void decrypt_validCiphertext_returnsOriginal() {
        String plaintext = "test password 123";
        String encrypted = encryption.encrypt(plaintext);
        String decrypted = encryption.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void encrypt_decrypt_roundTrip_preservesData() {
        String[] testStrings = {"", "short", "a longer string with spaces and symbols!@#$%", "日本語テスト"};
        for (String input : testStrings) {
            String encrypted = encryption.encrypt(input);
            String decrypted = encryption.decrypt(encrypted);
            assertThat(decrypted).isEqualTo(input);
        }
    }

    @Test
    void encrypt_sameInput_producesDifferentCiphertexts() {
        String plaintext = "same input";
        String encrypted1 = encryption.encrypt(plaintext);
        String encrypted2 = encryption.encrypt(plaintext);
        assertThat(encrypted1).isNotEqualTo(encrypted2);
    }

    @Test
    void decrypt_tamperedCiphertext_throwsException() {
        String encrypted = encryption.encrypt("test");
        byte[] bytes = Base64.getDecoder().decode(encrypted);
        bytes[bytes.length - 1] ^= 0xFF; // flip last byte
        String tampered = Base64.getEncoder().encodeToString(bytes);
        assertThatThrownBy(() -> encryption.decrypt(tampered))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Decryption failed");
    }

    @Test
    void decrypt_truncatedCiphertext_throwsException() {
        String encrypted = encryption.encrypt("test");
        byte[] bytes = Base64.getDecoder().decode(encrypted);
        byte[] truncated = new byte[5];
        System.arraycopy(bytes, 0, truncated, 0, 5);
        String truncatedB64 = Base64.getEncoder().encodeToString(truncated);
        assertThatThrownBy(() -> encryption.decrypt(truncatedB64))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void constructor_invalidKeyLength_throwsIllegalArgument() {
        String shortKey = Base64.getEncoder().encodeToString("short".getBytes());
        assertThatThrownBy(() -> new EncryptionConfig(shortKey, new MockEnvironment()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("256 bits");
    }

    @Test
    void constructor_invalidBase64_throwsException() {
        assertThatThrownBy(() -> new EncryptionConfig("not-valid-base64!!!", new MockEnvironment()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encrypt_emptyString_succeeds() {
        String encrypted = encryption.encrypt("");
        String decrypted = encryption.decrypt(encrypted);
        assertThat(decrypted).isEmpty();
    }

    @Test
    void encrypt_unicodeString_preservesCharacters() {
        String unicode = "Ünïcödé: 日本語 العربية 🌍";
        String encrypted = encryption.encrypt(unicode);
        String decrypted = encryption.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(unicode);
    }
}
