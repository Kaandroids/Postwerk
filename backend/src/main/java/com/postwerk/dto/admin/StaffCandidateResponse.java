package com.postwerk.dto.admin;

import java.util.UUID;

/** A regular (non-staff) user eligible to be granted staff access — Grant-access picker. */
public record StaffCandidateResponse(
        UUID id,
        String name,
        String email
) {}
