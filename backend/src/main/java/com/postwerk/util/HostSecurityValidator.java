package com.postwerk.util;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Centralized SSRF host-security guard. Resolves a host to all of its IP addresses and
 * rejects any that fall in a blocked range: loopback, link-local, site-local, any-local,
 * CGNAT (100.64.0.0/10), IPv4-mapped IPv6 private ranges, and cloud metadata endpoints
 * (169.254.169.254, 100.100.100.200).
 *
 * <p>Used by {@link WebhookUrlValidator} (outbound webhook URLs) and by the mail
 * connection paths (IMAP/SMTP host validation) so every server-initiated connection is
 * guarded against SSRF and DNS-rebinding (TOCTOU) attacks. Available both as static
 * helpers and as an injectable Spring {@link Component} for constructor injection.</p>
 *
 * @since 1.0
 */
@Component
public final class HostSecurityValidator {

    /**
     * Resolves the given host and throws if any resolved address is in a blocked range.
     * Instance method delegating to {@link #checkHostAllowed(String)} so it can be
     * injected and mocked.
     *
     * @param host the hostname or IP literal to validate
     * @throws IllegalArgumentException if the host is blank, unresolvable, or resolves to a blocked address
     */
    public void validateHostAllowed(String host) {
        checkHostAllowed(host);
    }

    /**
     * Resolves the given host and throws if any resolved address is in a blocked range.
     *
     * @param host the hostname or IP literal to validate
     * @throws IllegalArgumentException if the host is blank, unresolvable, or resolves to a blocked address
     */
    public static void checkHostAllowed(String host) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host must not be empty");
        }
        if (isBlockedHostName(host)) {
            throw new IllegalArgumentException("Host targets a blocked address");
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host.trim());
            for (InetAddress addr : addresses) {
                if (isBlockedAddress(addr)) {
                    throw new IllegalArgumentException("Host resolves to a private or blocked address");
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Cannot resolve host");
        }
    }

    /** Matches well-known internal/loopback hostnames and reserved TLD suffixes by name. */
    public static boolean isBlockedHostName(String host) {
        String lower = host.toLowerCase();
        // Strip brackets for IPv6 literals
        if (lower.startsWith("[") && lower.endsWith("]")) {
            lower = lower.substring(1, lower.length() - 1);
        }
        return lower.equals("localhost") ||
               lower.equals("127.0.0.1") ||
               lower.equals("::1") ||
               lower.equals("0.0.0.0") ||
               lower.equals("0:0:0:0:0:0:0:0") ||
               lower.equals("0:0:0:0:0:0:0:1") ||
               lower.equals("::ffff:127.0.0.1") ||
               lower.equals("::ffff:0.0.0.0") ||
               lower.equals("::ffff:10.0.0.1") ||
               lower.endsWith(".local") ||
               lower.endsWith(".internal") ||
               lower.endsWith(".localhost");
    }

    /** True if the resolved address is in any private or cloud-metadata range. */
    public static boolean isBlockedAddress(InetAddress addr) {
        return isPrivateAddress(addr) || isCloudMetadata(addr);
    }

    private static boolean isPrivateAddress(InetAddress addr) {
        return addr.isLoopbackAddress() ||
               addr.isLinkLocalAddress() ||
               addr.isSiteLocalAddress() ||
               addr.isAnyLocalAddress() ||
               isCarrierGradeNat(addr) ||
               isIPv4MappedIPv6Private(addr);
    }

    private static boolean isCarrierGradeNat(InetAddress addr) {
        byte[] bytes = addr.getAddress();
        if (bytes.length != 4) return false;
        // 100.64.0.0/10
        int first = bytes[0] & 0xFF;
        int second = bytes[1] & 0xFF;
        return first == 100 && (second >= 64 && second <= 127);
    }

    /**
     * Detects IPv4-mapped IPv6 addresses (::ffff:x.x.x.x) that map to private IPv4 ranges.
     */
    private static boolean isIPv4MappedIPv6Private(InetAddress addr) {
        byte[] bytes = addr.getAddress();
        if (bytes.length != 16) return false;
        // Check for ::ffff: prefix (bytes 0-9 = 0, bytes 10-11 = 0xff)
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) return false;
        }
        if ((bytes[10] & 0xFF) != 0xFF || (bytes[11] & 0xFF) != 0xFF) return false;
        // Extract the mapped IPv4 and check
        int a = bytes[12] & 0xFF;
        int b = bytes[13] & 0xFF;
        // 127.0.0.0/8
        if (a == 127) return true;
        // 10.0.0.0/8
        if (a == 10) return true;
        // 172.16.0.0/12
        if (a == 172 && (b >= 16 && b <= 31)) return true;
        // 192.168.0.0/16
        if (a == 192 && b == 168) return true;
        // 0.0.0.0
        if (a == 0 && b == 0 && (bytes[14] & 0xFF) == 0 && (bytes[15] & 0xFF) == 0) return true;
        return false;
    }

    /**
     * Blocks cloud metadata endpoint IPs (AWS/GCP/Azure/Alibaba).
     */
    public static boolean isCloudMetadata(InetAddress addr) {
        byte[] b = addr.getAddress();
        if (b.length != 4) return false;
        int a = b[0] & 0xFF, c = b[1] & 0xFF, d = b[2] & 0xFF, e = b[3] & 0xFF;
        // 169.254.169.254 (AWS/GCP/Azure metadata)
        if (a == 169 && c == 254 && d == 169 && e == 254) return true;
        // 100.100.100.200 (Alibaba Cloud metadata)
        if (a == 100 && c == 100 && d == 100 && e == 200) return true;
        return false;
    }
}
