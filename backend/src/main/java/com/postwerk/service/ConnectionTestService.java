package com.postwerk.service;

import com.postwerk.dto.ConnectionTestRequest;
import com.postwerk.dto.ConnectionTestResponse;
import com.postwerk.util.HostSecurityValidator;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * Service for verifying IMAP and SMTP mail server connectivity.
 * Tests connections with configurable timeouts and returns structured success/failure results.
 *
 * @since 1.0
 */
@Service
public class ConnectionTestService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionTestService.class);

    private final HostSecurityValidator hostSecurityValidator;

    public ConnectionTestService(HostSecurityValidator hostSecurityValidator) {
        this.hostSecurityValidator = hostSecurityValidator;
    }

    public ConnectionTestResponse test(ConnectionTestRequest request) {
        // SSRF guard: reject internal/private/metadata hosts before connecting.
        // Uses getAllByName (via the shared validator) so every resolved address is checked.
        try {
            hostSecurityValidator.validateHostAllowed(request.host());
        } catch (IllegalArgumentException e) {
            log.warn("Connection test rejected for host '{}': {}", request.host(), e.getMessage());
            return new ConnectionTestResponse(false, "Connection to internal/private addresses is not allowed");
        }

        try {
            if ("imap".equalsIgnoreCase(request.type())) {
                return testImap(request);
            } else if ("smtp".equalsIgnoreCase(request.type())) {
                return testSmtp(request);
            }
            return new ConnectionTestResponse(false, "Unknown connection type");
        } catch (Exception e) {
            // Never echo the raw provider/exception message back to the client — it can leak
            // internal details. Log server-side, return a generic failure to the caller.
            log.warn("Connection test failed for type '{}' host '{}'", request.type(), request.host(), e);
            return new ConnectionTestResponse(false, "Connection failed");
        }
    }

    private ConnectionTestResponse testImap(ConnectionTestRequest req) throws Exception {
        Properties props = new Properties();
        String protocol = req.ssl() ? "imaps" : "imap";
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".host", req.host());
        props.put("mail." + protocol + ".port", String.valueOf(req.port()));
        props.put("mail." + protocol + ".ssl.enable", String.valueOf(req.ssl()));
        props.put("mail." + protocol + ".connectiontimeout", "10000");
        props.put("mail." + protocol + ".timeout", "10000");

        Session session = Session.getInstance(props);
        Store store = session.getStore(protocol);
        store.connect(req.host(), req.port(), req.username(), req.password());
        store.close();
        return new ConnectionTestResponse(true, "IMAP connection successful");
    }

    private ConnectionTestResponse testSmtp(ConnectionTestRequest req) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.host", req.host());
        props.put("mail.smtp.port", String.valueOf(req.port()));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");

        if (req.ssl()) {
            if (req.port() == 465) {
                props.put("mail.smtp.ssl.enable", "true");
            } else {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
            }
        }

        Session session = Session.getInstance(props);
        Transport transport = session.getTransport("smtp");
        transport.connect(req.host(), req.port(), req.username(), req.password());
        transport.close();
        return new ConnectionTestResponse(true, "SMTP connection successful");
    }
}
