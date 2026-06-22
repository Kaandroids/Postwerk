package com.postwerk.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SSRF guard tests for {@link HostSecurityValidator}. Kept fully offline: every blocked case is
 * either a name-literal check or an IP literal ({@code InetAddress.getAllByName} parses IP literals
 * without a DNS lookup), so the suite is deterministic and needs no network. This locks the blocked
 * ranges — loopback, link-local, site-local, any-local, CGNAT (100.64/10) and cloud-metadata IPs —
 * that protect every server-initiated connection (webhooks, IMAP/SMTP) against SSRF.
 */
class HostSecurityValidatorTest {

    // ── empty / blank ────────────────────────────────────────────────────

    @Test
    void nullHost_rejected() {
        assertThatThrownBy(() -> HostSecurityValidator.checkHostAllowed(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void blankHost_rejected() {
        assertThatThrownBy(() -> HostSecurityValidator.checkHostAllowed("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── blocked by name (no DNS) ─────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "localhost", "127.0.0.1", "0.0.0.0",
            "::1", "[::1]",                      // IPv6 loopback (bracket-stripped)
            "::ffff:127.0.0.1", "::ffff:10.0.0.1",
            "db.local", "service.internal", "api.localhost",
    })
    void blockedHostNames_areRejected(String host) {
        assertThat(HostSecurityValidator.isBlockedHostName(host)).isTrue();
        assertThatThrownBy(() -> HostSecurityValidator.checkHostAllowed(host))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "example.com", "api.example.org", "8.8.8.8", "sub.shop.de" })
    void publicHostNames_passNameCheck(String host) {
        assertThat(HostSecurityValidator.isBlockedHostName(host)).isFalse();
    }

    // ── blocked by resolved IP literal (no DNS) ──────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "10.0.0.1",        // 10/8 site-local
            "172.16.0.1",      // 172.16/12 site-local
            "172.31.255.255",
            "192.168.1.1",     // 192.168/16 site-local
            "169.254.1.1",     // link-local
            "169.254.169.254", // AWS/GCP/Azure metadata
            "100.100.100.200", // Alibaba metadata
            "100.64.0.1",      // CGNAT 100.64/10 lower bound
            "100.127.255.255", // CGNAT upper bound
    })
    void privateAndMetadataIps_areRejected(String ip) {
        assertThatThrownBy(() -> HostSecurityValidator.checkHostAllowed(ip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blocked");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "8.8.8.8",          // public DNS
            "1.1.1.1",          // public DNS
            "100.63.255.255",   // just below CGNAT (100.64/10) → public
            "100.128.0.1",      // just above CGNAT → public
    })
    void publicIps_areAllowed(String ip) {
        assertThatCode(() -> HostSecurityValidator.checkHostAllowed(ip)).doesNotThrowAnyException();
    }

    // ── instance method delegates to the static guard ────────────────────

    @Test
    void instanceMethod_delegatesToStaticGuard() {
        HostSecurityValidator validator = new HostSecurityValidator();
        assertThatThrownBy(() -> validator.validateHostAllowed("127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> validator.validateHostAllowed("8.8.8.8")).doesNotThrowAnyException();
    }

    // ── static address helpers ───────────────────────────────────────────

    @Test
    void isCloudMetadata_matchesOnlyMetadataIps() throws Exception {
        assertThat(HostSecurityValidator.isCloudMetadata(InetAddress.getByName("169.254.169.254"))).isTrue();
        assertThat(HostSecurityValidator.isCloudMetadata(InetAddress.getByName("100.100.100.200"))).isTrue();
        assertThat(HostSecurityValidator.isCloudMetadata(InetAddress.getByName("8.8.8.8"))).isFalse();
        assertThat(HostSecurityValidator.isCloudMetadata(InetAddress.getByName("169.254.1.1"))).isFalse();
    }

    @Test
    void isBlockedAddress_coversPrivateAndMetadata() throws Exception {
        assertThat(HostSecurityValidator.isBlockedAddress(InetAddress.getByName("10.0.0.1"))).isTrue();
        assertThat(HostSecurityValidator.isBlockedAddress(InetAddress.getByName("127.0.0.1"))).isTrue();
        assertThat(HostSecurityValidator.isBlockedAddress(InetAddress.getByName("169.254.169.254"))).isTrue();
        assertThat(HostSecurityValidator.isBlockedAddress(InetAddress.getByName("100.64.0.1"))).isTrue();
        assertThat(HostSecurityValidator.isBlockedAddress(InetAddress.getByName("8.8.8.8"))).isFalse();
    }
}
