package com.postwerk.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Mandatory staff reason captured for a destructive moderation action (take-down / review delete). */
public record ModerationActionRequest(
        @NotBlank @Size(max = 500) String reason
) {}
