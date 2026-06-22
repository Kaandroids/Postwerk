package com.postwerk.dto;

import java.util.Map;

/** DTO representing an AI function call with its name, arguments, and result. */
public record AiToolCallDto(
        String tool,
        Map<String, Object> args,
        Object result,
        boolean success,
        Object validationIssues
) {
    public AiToolCallDto(String tool, Map<String, Object> args, Object result, boolean success) {
        this(tool, args, result, success, null);
    }
}
