package com.postwerk.model.enums;

/**
 * The GDPR right a data-subject access request (DSAR) exercises.
 *
 * @since 1.0
 */
public enum DataRequestType {
    /** Art. 15 / 20 — access + portability: a machine-readable copy of all data held. */
    EXPORT,
    /** Art. 17 — erasure ("right to be forgotten"): delete the subject's personal data. */
    ERASURE,
    /** Art. 16 — rectification: correct inaccurate personal data. */
    RECTIFICATION,
    /** Art. 18 — restriction: freeze processing pending review. */
    RESTRICTION,
    /** Art. 15 — access: confirm what data is processed. */
    ACCESS
}
