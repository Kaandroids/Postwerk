package com.postwerk.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.*;

class WebhookUrlValidatorTest {

    // ── Valid URLs ───────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "https://api.stripe.com/v1/webhook",
            "http://my-service.com:8080/hook",
            "https://hooks.slack.com/services/T00/B00/xxx",
            "http://example.com:443/path?key=value",
            "https://example.com:8443/callback"
    })
    void validate_validUrls_returnsInetAddress(String url) {
        InetAddress result = WebhookUrlValidator.validate(url);
        assertThat(result).isNotNull();
    }

    // ── Reject null/empty/blank ─────────────────────────────────────────

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void validate_nullOrBlank_throws(String url) {
        assertThatThrownBy(() -> WebhookUrlValidator.validate(url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be empty");
    }

    // ── Reject localhost / loopback ─────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost",
            "http://localhost:8080",
            "https://localhost/path",
            "http://127.0.0.1",
            "http://127.0.0.1:8080/hook",
            "http://[::1]",
            "http://0.0.0.0"
    })
    void validate_localhost_throws(String url) {
        assertThatThrownBy(() -> WebhookUrlValidator.validate(url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blocked");
    }

    // ── Reject private IPs (resolved addresses) ────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "http://192.168.1.1",
            "http://10.0.0.1",
            "http://172.16.0.1"
    })
    void validate_privateIps_throws(String url) {
        assertThatThrownBy(() -> WebhookUrlValidator.validate(url))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Reject link-local / cloud metadata ──────────────────────────────

    @Test
    void validate_awsMetadataEndpoint_throws() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("http://169.254.169.254"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_linkLocalAddress_throws() {
        // 169.254.x.x is link-local — detected by InetAddress.isLinkLocalAddress()
        assertThatThrownBy(() -> WebhookUrlValidator.validate("http://169.254.1.1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Reject non-HTTP schemes ─────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://example.com",
            "file:///etc/passwd",
            "javascript:alert(1)",
            "gopher://evil.com"
    })
    void validate_nonHttpScheme_throws(String url) {
        assertThatThrownBy(() -> WebhookUrlValidator.validate(url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS or HTTP");
    }

    @Test
    void validate_dataUri_throws() {
        // data: URIs fail at URI.create() due to invalid syntax
        assertThatThrownBy(() -> WebhookUrlValidator.validate("data:text/html,<h1>hi</h1>"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Reject blocked host patterns ────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "http://service.local",
            "http://app.internal",
            "http://test.localhost"
    })
    void validate_blockedHostSuffixes_throws(String url) {
        assertThatThrownBy(() -> WebhookUrlValidator.validate(url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blocked");
    }

    // ── Reject non-standard ports ───────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com:22",
            "https://example.com:3306",
            "https://example.com:6379",
            "https://example.com:5432"
    })
    void validate_nonStandardPort_throws(String url) {
        assertThatThrownBy(() -> WebhookUrlValidator.validate(url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-standard port");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com:80",
            "https://example.com:443",
            "https://example.com:8080",
            "https://example.com:8443"
    })
    void validate_allowedPorts_doesNotThrow(String url) {
        assertThatDoesNotThrow(() -> WebhookUrlValidator.validate(url));
    }

    // ── Reject IPv6-mapped loopback & private ───────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "http://[0:0:0:0:0:0:0:0]",
            "http://[0:0:0:0:0:0:0:1]",
            "http://[::ffff:127.0.0.1]",
            "http://[::ffff:0.0.0.0]",
            "http://[::ffff:10.0.0.1]"
    })
    void validate_ipv6MappedBlocked_throws(String url) {
        assertThatThrownBy(() -> WebhookUrlValidator.validate(url))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Edge cases ──────────────────────────────────────────────────────

    @Test
    void validate_urlWithUserInfo_resolvesOrRejects() {
        // URL with credentials — host is still "example.com" which is valid
        InetAddress result = WebhookUrlValidator.validate("http://user:pass@example.com");
        assertThat(result).isNotNull();
    }

    @Test
    void validate_malformedUrl_throws() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("not a url at all"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_unresolvableHost_throws() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://this-host-definitely-does-not-exist-xyz123.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot resolve");
    }

    @Test
    void validate_defaultPort_notChecked() {
        // Port -1 (default/omitted) should be allowed
        InetAddress result = WebhookUrlValidator.validate("https://example.com/webhook");
        assertThat(result).isNotNull();
    }

    // ── Carrier-grade NAT (100.64.0.0/10) ───────────────────────────────

    @Test
    void validate_carrierGradeNat_throws() {
        // 100.64.x.x through 100.127.x.x — these resolve to themselves as IP literals
        assertThatThrownBy(() -> WebhookUrlValidator.validate("http://100.100.100.200"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertThatDoesNotThrow(org.junit.jupiter.api.function.Executable executable) {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(executable);
    }
}
