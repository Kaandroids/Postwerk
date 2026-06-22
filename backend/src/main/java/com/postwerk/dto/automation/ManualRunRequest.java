package com.postwerk.dto.automation;

import java.util.Map;

/**
 * Request body for manually firing an automation's MANUAL trigger.
 *
 * @param parameters user-entered values keyed by the trigger's parameter-set field names; each is seeded
 *                   as a {@code trigger.<name>} execution variable. May be {@code null}/empty when the
 *                   manual trigger has no parameter set.
 */
public record ManualRunRequest(Map<String, Object> parameters) {
}
