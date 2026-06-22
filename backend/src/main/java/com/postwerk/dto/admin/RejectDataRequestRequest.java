package com.postwerk.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Reason a DSAR is being rejected — captured for the audit log and (in a real flow) sent to the requester.
 *
 * @since 1.0
 */
public record RejectDataRequestRequest(
        @NotBlank @Size(max = 4000) String reason
) {}
