package com.postwerk.model.enums;

/**
 * The UI target a {@link com.postwerk.model.QuotaOverride} was created against. Enforcement is always
 * organization-scoped: a {@code USER} target resolves to that user's personal organization, an
 * {@code ORG} target enforces on itself.
 *
 * @since 1.0
 */
public enum QuotaOverrideTargetType {
    USER,
    ORG
}
