package com.postwerk.service.executor;

import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {{variableKey}} placeholders in template strings using
 * the unified variable system from ExecutionContext.
 *
 * <p>Supports both new-style variables (email.from, extraction_0.amount)
 * and legacy placeholders (fromAddress, subject) for backward compatibility.</p>
 *
 * @since 2.0
 */
@Component
public class VariableResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^}]+)}}");

    /**
     * Resolves all {{key}} placeholders in the template using context variables.
     */
    public String resolve(String template, ExecutionContext context) {
        return resolve(template, context, false);
    }

    /**
     * Resolves all {{key}} placeholders with URL-safe encoding of values.
     */
    public String resolveUrlSafe(String template, ExecutionContext context) {
        return resolve(template, context, true);
    }

    private String resolve(String template, ExecutionContext context, boolean urlEncode) {
        if (template == null || template.isBlank()) return "";

        Map<String, Object> variables = context.getVariables();

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String resolved = resolveKey(key, variables, context);
            if (urlEncode) {
                resolved = URLEncoder.encode(resolved, StandardCharsets.UTF_8);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(resolved));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String resolveKey(String key, Map<String, Object> variables, ExecutionContext context) {
        // 1. Try direct variable lookup
        Object value = variables.get(key);
        if (value != null) return String.valueOf(value);

        // 2. Legacy placeholder mapping
        String mapped = mapLegacyKey(key);
        if (mapped != null) {
            value = variables.get(mapped);
            if (value != null) return String.valueOf(value);
        }

        // 3. Legacy: try flat extraction data (for backward compat with old templates)
        Map<String, Object> extracted = context.getAllExtractedData();
        Object extractedValue = extracted.get(key);
        if (extractedValue != null) return String.valueOf(extractedValue);

        return "";
    }

    /**
     * Maps legacy placeholder names to new variable keys.
     */
    private String mapLegacyKey(String key) {
        return switch (key) {
            case "fromAddress" -> "email.from";
            case "fromName" -> "email.fromName";
            case "subject" -> "email.subject";
            case "toAddress" -> "email.to";
            case "ccAddress" -> "email.cc";
            case "body" -> "email.body";
            case "receivedAt" -> "email.receivedAt";
            default -> null;
        };
    }
}
