package com.postwerk.model.enums;

/**
 * Lifecycle state of a {@code Membership} (multi-tenant model #4).
 *
 * @since 1.0
 */
public enum MembershipStatus {
    /** Invited but not yet accepted; no access granted. */
    INVITED,
    /** Active member; role permissions apply. */
    ACTIVE,
    /** Temporarily disabled by an admin; access denied without losing the row/grants. */
    SUSPENDED
}
