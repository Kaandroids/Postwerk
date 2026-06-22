package com.postwerk.util;

import java.util.Set;

/**
 * Defines the set of parameter names reserved by the system for email metadata injection.
 *
 * <p>These names (e.g., {@code fromAddress}, {@code subject}) are automatically populated
 * by the EXTRACT node and must not be used as user-defined parameter names in a
 * {@link com.postwerk.model.ParameterSet}.</p>
 *
 * @since 1.0
 */
public final class ReservedParamNames {
    private ReservedParamNames() {}

    public static final Set<String> RESERVED = Set.of(
            "fromAddress", "fromName", "subject", "toAddress", "receivedAt"
    );
}
