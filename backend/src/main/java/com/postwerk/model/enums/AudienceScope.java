package com.postwerk.model.enums;

/**
 * Targeting scope shared by announcements and feature flags: everyone, specific plans, a single
 * organization, or platform staff only.
 */
public enum AudienceScope {
    EVERYONE,
    PLAN,
    ORG,
    STAFF
}
