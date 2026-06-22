package com.postwerk.service;

import com.postwerk.model.User;
import com.postwerk.service.email.EmailMessage;
import com.postwerk.service.email.EmailSendException;
import com.postwerk.service.email.EmailSender;
import com.postwerk.service.email.MailTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Composes and sends the account-lifecycle emails (verification, password reset) in the
 * recipient's language, then dispatches via the shared {@link EmailSender}.
 *
 * <p>Sends are best-effort: a transport failure is logged but never propagated, so registration /
 * reset flows stay robust and never leak account existence. Users can re-trigger via "resend".</p>
 */
@Service
public class AuthMailService {

    private static final Logger log = LoggerFactory.getLogger(AuthMailService.class);

    private final EmailSender emailSender;
    private final MailTemplateService templates;
    private final String frontendBaseUrl;

    public AuthMailService(EmailSender emailSender,
                           MailTemplateService templates,
                           @Value("${app.frontend-base-url:http://localhost:4200}") String frontendBaseUrl) {
        this.emailSender = emailSender;
        this.templates = templates;
        this.frontendBaseUrl = stripTrailingSlash(frontendBaseUrl);
    }

    public void sendVerificationEmail(User user, String token, String lang) {
        String language = normalize(lang);
        String link = frontendBaseUrl + "/auth/verify-email?token=" + encode(token);
        Map<String, String> vars = Map.of(
                "name", displayName(user),
                "link", link,
                "expiryHours", "24"
        );
        String subject = "de".equals(language) ? "Bestätige deine Postwerk-E-Mail" : "Confirm your Postwerk email";
        send(user.getEmail(), subject, "verify-email", language, vars);
    }

    public void sendPasswordResetEmail(User user, String token, String lang) {
        String language = normalize(lang);
        String link = frontendBaseUrl + "/auth/reset-password?token=" + encode(token);
        Map<String, String> vars = Map.of(
                "name", displayName(user),
                "link", link,
                "expiryHours", "1"
        );
        String subject = "de".equals(language) ? "Setze dein Postwerk-Passwort zurück" : "Reset your Postwerk password";
        send(user.getEmail(), subject, "reset-password", language, vars);
    }

    private void send(String to, String subject, String templateBase, String language, Map<String, String> vars) {
        try {
            String html = templates.load(templateBase + "." + language + ".html", vars);
            String text = templates.load(templateBase + "." + language + ".txt", vars);
            emailSender.send(new EmailMessage(to, subject, html, text));
        } catch (EmailSendException e) {
            log.error("Failed to send '{}' email to {}: {}", templateBase, to, e.getMessage());
        }
    }

    private static String displayName(User user) {
        String name = user.getFullName();
        if (name == null || name.isBlank()) {
            return user.getEmail();
        }
        // First name only keeps the greeting friendly.
        return name.trim().split("\\s+")[0];
    }

    private static String normalize(String lang) {
        return lang != null && lang.toLowerCase().startsWith("de") ? "de" : "en";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
