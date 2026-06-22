package com.postwerk.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads prompt templates from classpath resources and resolves {{placeholder}} variables.
 * Templates are cached in memory after first load.
 */
@Service
public class PromptService {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Load a prompt template and resolve placeholders.
     *
     * @param templateName file name under src/main/resources/prompts/
     * @param vars         map of placeholder keys to replacement values
     * @return resolved prompt string
     */
    public String load(String templateName, Map<String, String> vars) {
        String template = cache.computeIfAbsent(templateName, this::readTemplate);
        String result = template;
        for (var entry : vars.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    /**
     * Load a prompt template without placeholder resolution.
     */
    public String load(String templateName) {
        return load(templateName, Map.of());
    }

    private String readTemplate(String name) {
        try (var is = getClass().getResourceAsStream("/prompts/" + name)) {
            if (is == null) {
                throw new IllegalStateException("Prompt template not found: " + name);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read prompt: " + name, e);
        }
    }
}
