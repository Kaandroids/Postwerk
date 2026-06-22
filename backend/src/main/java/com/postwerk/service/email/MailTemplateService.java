package com.postwerk.service.email;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads email body templates from classpath {@code /email/} and resolves {{placeholder}} variables.
 * Templates are cached after first load. Mirrors {@code PromptService} but for the mail directory,
 * so transactional-email and (later) notification-email composers share one loader.
 */
@Service
public class MailTemplateService {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Load an email template and resolve placeholders.
     *
     * @param templateName file name under {@code src/main/resources/email/} (e.g. {@code verify-email.en.html})
     * @param vars         placeholder keys → replacement values
     * @return resolved template content
     */
    public String load(String templateName, Map<String, String> vars) {
        String template = cache.computeIfAbsent(templateName, this::readTemplate);
        String result = template;
        for (var entry : vars.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private String readTemplate(String name) {
        try (var is = getClass().getResourceAsStream("/email/" + name)) {
            if (is == null) {
                throw new IllegalStateException("Email template not found: " + name);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read email template: " + name, e);
        }
    }
}
