package com.postwerk.util;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * Utility for resolving the originating client IP address from an HTTP request.
 *
 * <p>Only trusts the {@code X-Forwarded-For} header when the direct connection comes from
 * a known trusted proxy (loopback, Docker bridge network). This prevents IP spoofing attacks
 * where clients forge the header to bypass rate limiting and audit logging.</p>
 *
 * @since 1.0
 */
public final class IpResolverUtil {

    private static final Set<String> TRUSTED_PROXIES = Set.of(
            "127.0.0.1", "::1", "0:0:0:0:0:0:0:1",
            // Docker default bridge network
            "172.17.0.1", "172.18.0.1", "172.19.0.1", "172.20.0.1"
    );

    private IpResolverUtil() {}

    public static String extractIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        // Only trust X-Forwarded-For if the direct connection is from a trusted proxy
        if (isTrustedProxy(remoteAddr)) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                // Take the rightmost non-trusted IP (closest to our proxy)
                String[] parts = xForwardedFor.split(",");
                for (int i = parts.length - 1; i >= 0; i--) {
                    String ip = parts[i].trim();
                    if (!ip.isBlank() && !isTrustedProxy(ip)) {
                        return ip;
                    }
                }
                // All IPs in chain are trusted — use the first one
                return parts[0].trim();
            }
        }

        return remoteAddr;
    }

    private static boolean isTrustedProxy(String ip) {
        if (ip == null) return false;
        if (TRUSTED_PROXIES.contains(ip)) return true;
        // Docker bridge networks: 172.16.0.0/12
        if (ip.startsWith("172.")) {
            try {
                int second = Integer.parseInt(ip.split("\\.")[1]);
                if (second >= 16 && second <= 31) return true;
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }
}
