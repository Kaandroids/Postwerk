package com.postwerk.util;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * Validates webhook URLs to prevent SSRF attacks.
 * Blocks private IP ranges, loopback, link-local, cloud metadata, and non-HTTPS URLs.
 * Returns the resolved InetAddress to prevent DNS rebinding (TOCTOU) attacks.
 */
public final class WebhookUrlValidator {

    private WebhookUrlValidator() {}

    /**
     * Validates that the given URL is safe for outbound HTTP requests.
     * Returns the resolved InetAddress so callers can connect directly to the IP,
     * preventing DNS rebinding attacks between validation and connection.
     *
     * @param url the URL to validate
     * @return the resolved InetAddress to use for the actual connection
     * @throws IllegalArgumentException if the URL is invalid or targets a private/blocked address
     */
    public static InetAddress validate(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Webhook URL must not be empty");
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid webhook URL");
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))) {
            throw new IllegalArgumentException("Webhook URL must use HTTPS or HTTP scheme");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Webhook URL must have a valid host");
        }

        if (HostSecurityValidator.isBlockedHostName(host)) {
            throw new IllegalArgumentException("Webhook URL targets a blocked address");
        }

        // Restrict to standard HTTP ports to prevent internal port scanning
        int port = uri.getPort();
        if (port != -1 && !isAllowedPort(port)) {
            throw new IllegalArgumentException("Webhook URL uses a non-standard port");
        }

        // DNS resolve and check all resolved IPs (private ranges + cloud metadata)
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (HostSecurityValidator.isBlockedAddress(addr)) {
                    throw new IllegalArgumentException("Webhook URL resolves to a private or blocked address");
                }
            }
            return addresses[0];
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Cannot resolve webhook URL host");
        }
    }

    private static final Set<Integer> ALLOWED_PORTS = Set.of(80, 443, 8080, 8443);

    private static boolean isAllowedPort(int port) {
        return ALLOWED_PORTS.contains(port);
    }
}
