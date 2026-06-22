package com.postwerk.model.enums;

/** Category of a feature flag. */
public enum FeatureFlagKind {
    /** Gradual feature release / rollout. */
    RELEASE,
    /** Operational kill-switch / guard. */
    OPS,
    /** A/B experiment. */
    EXPERIMENT,
    /** Entitlement-style permission gate. */
    PERMISSION
}
