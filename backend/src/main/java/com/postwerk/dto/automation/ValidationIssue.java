package com.postwerk.dto.automation;

/**
 * A single semantic validation problem detected on an automation flow by the
 * {@link com.postwerk.service.AutomationValidator}.
 *
 * @param code     stable issue code from the shared rule catalog (e.g. {@code MISSING_TRIGGER})
 * @param severity {@code "error"} (blocks activate/publish) or {@code "warning"} (advisory only)
 * @param nodeId   the offending node id, or {@code null} for automation-level issues
 * @param message  a human-readable English description (the frontend localises via the code)
 */
public record ValidationIssue(String code, String severity, String nodeId, String message) {

    public static final String SEVERITY_ERROR = "error";
    public static final String SEVERITY_WARNING = "warning";

    public static ValidationIssue error(String code, String nodeId, String message) {
        return new ValidationIssue(code, SEVERITY_ERROR, nodeId, message);
    }

    public static ValidationIssue warning(String code, String nodeId, String message) {
        return new ValidationIssue(code, SEVERITY_WARNING, nodeId, message);
    }

    public boolean isError() {
        return SEVERITY_ERROR.equals(severity);
    }
}
